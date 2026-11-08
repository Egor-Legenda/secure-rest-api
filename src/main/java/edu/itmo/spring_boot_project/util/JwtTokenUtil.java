package edu.itmo.spring_boot_project.util;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
@Component
public final class JwtTokenUtil {
    private final Key key;
    private final long expiration;
    public JwtTokenUtil(@Value("${jwt.secret:}") String secret, @Value("${jwt.expiration:3600000}") long expiration) {
        key = secret.isBlank() ? Keys.secretKeyFor(SignatureAlgorithm.HS256)
            : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }
    public String generateToken(String username) {
        return Jwts.builder().setSubject(username).setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(key, SignatureAlgorithm.HS256).compact();
    }
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
    }
}
