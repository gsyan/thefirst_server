//--------------------------------------------------------------------------------------------------
package com.bk.sbs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-validity-in-seconds}")
    private long accessTokenValidity;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenValidity;

    private Key getSigningKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS512.getJcaName());
    }

    // 로그인 시 accountId만 포함된 토큰 생성
    public String createAccessToken(Long accountId) {
        return createToken(accountId, null, accessTokenValidity, null);
    }

    // 리프레시 토큰은 회전/재사용 감지를 위해 jti(고유 id)를 부여
    public String createRefreshToken(Long accountId) {
        return createToken(accountId, null, refreshTokenValidity, UUID.randomUUID().toString());
    }

    // 유예 기간 재시도용 — 새 jti를 발급하지 않고 지정된 jti(현재 활성 세션)를 그대로 사용
    public String createRefreshTokenWithJti(Long accountId, String jti) {
        return createToken(accountId, null, refreshTokenValidity, jti);
    }

    // 캐릭터 선택 후 commanderId까지 포함된 토큰 생성
    public String createAccessTokenWithCommander(Long accountId, Long commanderId) {
        return createToken(accountId, commanderId, accessTokenValidity, null);
    }

    public String createRefreshTokenWithCommander(Long accountId, Long commanderId) {
        return createToken(accountId, commanderId, refreshTokenValidity, UUID.randomUUID().toString());
    }

    public String createRefreshTokenWithCommanderAndJti(Long accountId, Long commanderId, String jti) {
        return createToken(accountId, commanderId, refreshTokenValidity, jti);
    }

    // subject = accountId (불변값), email 제거
    private String createToken(Long accountId, Long commanderId, long validity, String jti) {
        Claims claims = Jwts.claims().setSubject(accountId.toString());
        if (commanderId != null) {
            claims.put("commanderId", commanderId);
        }
        if (jti != null) {
            claims.put("jti", jti);
        }
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validity * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // subject에서 accountId 추출
    public Long getAccountIdFromSubject(String token) {
        return Long.parseLong(getClaimsFromToken(token).getSubject());
    }

    public Long getCommanderIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("commanderId", Long.class);
    }

    public boolean hasCommanderId(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("commanderId") != null;
    }

    // 리프레시 토큰의 jti 추출 (구버전 토큰은 jti 클레임이 없어 null)
    public String getJtiFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("jti", String.class);
    }

    public long getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    public String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


