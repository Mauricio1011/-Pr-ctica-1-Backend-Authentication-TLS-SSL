package com.example.securebackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final String issuer;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret debe tener al menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = issuer;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generate(String username, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        List<String> roles = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public List<String> extractRoles(Claims claims) {
        Object raw = claims.get(ROLES_CLAIM);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }
}
