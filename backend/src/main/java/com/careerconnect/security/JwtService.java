package com.careerconnect.security;

import com.careerconnect.common.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final int expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:30}") int expirationMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 bytes (256 bits) for HMAC-SHA256.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(SecurityUser securityUser) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", securityUser.getUserId());
        claims.put("email", securityUser.getUsername());
        claims.put("role", securityUser.getRole().name());
        claims.put("fullName", securityUser.getFullName());

        if (securityUser.getCandidateId() != null) {
            claims.put("candidateId", securityUser.getCandidateId());
        }
        if (securityUser.getRecruiterId() != null) {
            claims.put("recruiterId", securityUser.getRecruiterId());
        }
        if (securityUser.getCompanyId() != null) {
            claims.put("companyId", securityUser.getCompanyId());
        }

        return Jwts.builder()
                .subject(securityUser.getUsername())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Instant getExpirationTimestamp(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getExpiration().toInstant();
    }

    public Optional<SecurityUser> validateAndExtractSecurityUser(String token) {
        try {
            Claims claims = extractAllClaims(token);

            if (claims.getExpiration().before(new Date())) {
                log.warn("JWT token has expired");
                return Optional.empty();
            }

            Long userId = ((Number) claims.get("userId")).longValue();
            String email = claims.get("email", String.class);
            String roleStr = claims.get("role", String.class);
            String fullName = claims.get("fullName", String.class);

            Role role = Role.valueOf(roleStr);

            Long candidateId = claims.get("candidateId") != null
                    ? ((Number) claims.get("candidateId")).longValue()
                    : null;

            Long recruiterId = claims.get("recruiterId") != null
                    ? ((Number) claims.get("recruiterId")).longValue()
                    : null;

            Long companyId = claims.get("companyId") != null
                    ? ((Number) claims.get("companyId")).longValue()
                    : null;

            SecurityUser user = new SecurityUser(
                    userId,
                    email,
                    "", // password hash not stored in token
                    role,
                    fullName,
                    candidateId,
                    recruiterId,
                    companyId,
                    true
            );

            return Optional.of(user);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
