package com.quizsphere.security;

import com.quizsphere.config.AppProperties;
import com.quizsphere.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        byte[] material;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET_KEY is not set: using a random per-process key. Tokens will not survive restarts. "
                    + "Set JWT_SECRET_KEY in every non-local environment.");
            material = new byte[48];
            new SecureRandom().nextBytes(material);
        } else {
            material = sha256(secret);
        }
        this.key = Keys.hmacShaKeyFor(material);
        this.ttl = Duration.ofMinutes(props.jwt().expirationMinutes());
    }

    public record Issued(String token, Instant expiresAt) {
    }

    public Issued issue(Long userId, Role role) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new Issued(token, exp);
    }

    /** Returns the token claims if the signature and expiry are valid. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
