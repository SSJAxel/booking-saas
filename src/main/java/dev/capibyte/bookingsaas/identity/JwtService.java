package dev.capibyte.bookingsaas.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

	private final SecretKey key;
	private final long expirationMinutes;

	public JwtService(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMinutes = expirationMinutes;
	}

	public String generateToken(UUID userId, UUID tenantId, String email, Role role) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.claim("tenantId", tenantId.toString())
				.claim("email", email)
				.claim("role", role.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
				.signWith(key)
				.compact();
	}

	public Claims parseClaims(String token) {
		Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
		return jws.getPayload();
	}
}
