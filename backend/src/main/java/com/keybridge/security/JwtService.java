package com.keybridge.security;

import com.keybridge.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiryMs;

    public JwtService(
            @Value("${keybridge.jwt.secret}") String secret,
            @Value("${keybridge.jwt.expiry-minutes}") long mins
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // HS256 needs a 256-bit key; never pad a weak secret.
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
                    + " bytes, got " + keyBytes.length + ". Generate one with: openssl rand -hex 32");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiryMs = mins * 60_000;
    }

    public String generate(User u) {
        return Jwts.builder()
                .subject(u.getEmail())
                .claim("role", u.getRole().name())
                .claim("userId", u.getId())
                .claim("name", u.getName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }

    public String subject(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean validate(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
