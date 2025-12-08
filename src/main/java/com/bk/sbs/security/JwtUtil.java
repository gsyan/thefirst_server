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
    public String createAccessToken(String email, Long accountId) {
        return createToken(email, accountId, null, accessTokenValidity);
    }

    public String createRefreshToken(String email, Long accountId) {
        return createToken(email, accountId, null, refreshTokenValidity);
    }

    // 캐릭터 선택 후 characterId까지 포함된 토큰 생성
    public String createAccessTokenWithCharacter(String email, Long accountId, Long characterId) {
        return createToken(email, accountId, characterId, accessTokenValidity);
    }

    public String createRefreshTokenWithCharacter(String email, Long accountId, Long characterId) {
        return createToken(email, accountId, characterId, refreshTokenValidity);
    }

    private String createToken(String email, Long accountId, Long characterId, long validity) {
        Claims claims = Jwts.claims().setSubject(email);
        claims.put("accountId", accountId);
        if (characterId != null) {
            claims.put("characterId", characterId);
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

    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    public Long getAccountIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("accountId", Long.class);
    }

    public Long getCharacterIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("characterId", Long.class);
    }

    public boolean hasCharacterId(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("characterId") != null;
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
