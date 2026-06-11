package com.smartdorm.backend.util;

import com.smartdorm.backend.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenUtil {

    @Value("${app.auth.jwt-secret}")
    private String jwtSecret;

    @Value("${app.auth.token-expire-hours:24}")
    private long tokenExpireHours;

    public String generateToken(User user, boolean dormLeader) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("userType", user.getUserType())
                .claim("leader", dormLeader)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(tokenExpireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}