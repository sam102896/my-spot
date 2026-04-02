package com.spot.security;

import com.spot.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(String userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getTtlSeconds());
        return Jwts.builder().issuer(props.getJwt().getIssuer()).subject(userId).issuedAt(Date.from(now))
                .expiration(Date.from(exp)).signWith(key).compact();
    }

    public String parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
