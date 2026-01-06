package com.isp.platform.gateway.security;

import com.isp.platform.gateway.auth.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long accessTtlMinutes;
    private final long refreshTtlMinutes;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${security.jwt.refresh-ttl-minutes:43200}") long refreshTtlMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlMinutes = refreshTtlMinutes;
    }

    public String generateAccessToken(UserAccount user) {
        return buildToken(user, accessTtlMinutes, TokenType.ACCESS);
    }

    public String generateRefreshToken(UserAccount user) {
        return buildToken(user, refreshTtlMinutes, TokenType.REFRESH);
    }

    private String buildToken(UserAccount user, long ttlMinutes, TokenType type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .claim("tenant_id", user.getTenantId().toString())
                .claim("roles", user.getRoles())
                .claim("typ", type.name())
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Authentication toAuthentication(String token) {
        Claims claims = getClaims(token);
        String username = claims.getSubject();
        Set<Role> roles = claims.get("roles", Set.class);
        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    public UUID resolveTenant(String token) {
        Claims claims = getClaims(token);
        String tenantId = claims.get("tenant_id", String.class);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public boolean isRefreshToken(String token) {
        Claims claims = getClaims(token);
        String type = claims.get("typ", String.class);
        return TokenType.REFRESH.name().equals(type);
    }

    public Claims getClaims(String token) {
        return parse(token).getBody();
    }

    public boolean validate(String token) {
        parse(token);
        return true;
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
    }
}
