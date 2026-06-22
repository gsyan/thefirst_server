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
        return createToken(accountId, null, accessTokenValidity);
    }

    public String createRefreshToken(Long accountId) {
        return createToken(accountId, null, refreshTokenValidity);
    }

    // 캐릭터 선택 후 commanderId까지 포함된 토큰 생성
    public String createAccessTokenWithCommander(Long accountId, Long commanderId) {
        return createToken(accountId, commanderId, accessTokenValidity);
    }

    public String createRefreshTokenWithCommander(Long accountId, Long commanderId) {
        return createToken(accountId, commanderId, refreshTokenValidity);
    }

    // subject = accountId (불변값), email 제거
    private String createToken(Long accountId, Long commanderId, long validity) {
        Claims claims = Jwts.claims().setSubject(accountId.toString());
        if (commanderId != null) {
            claims.put("commanderId", commanderId);
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


