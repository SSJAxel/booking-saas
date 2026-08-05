package dev.capibyte.bookingsaas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private final JwtService jwtService =
			new JwtService("unit-test-secret-key-must-be-at-least-32-bytes-long", 60);

	@Test
	void tokenRoundTripsAllClaims() {
		UUID userId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();

		String token = jwtService.generateToken(userId, tenantId, "owner@example.com", Role.OWNER, true);
		Claims claims = jwtService.parseClaims(token);

		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
		assertThat(claims.get("email", String.class)).isEqualTo("owner@example.com");
		assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
		assertThat(claims.get("platformAdmin", Boolean.class)).isTrue();
	}

	@Test
	void rejectsATokenSignedWithADifferentKey() {
		JwtService otherIssuer = new JwtService("a-completely-different-secret-key-of-32-bytes+", 60);
		String token = otherIssuer.generateToken(UUID.randomUUID(), UUID.randomUUID(), "x@example.com", Role.STAFF, false);

		assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsATamperedToken() {
		String token = jwtService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "x@example.com", Role.STAFF, false);
		String tampered = token.substring(0, token.length() - 4) + "abcd";

		assertThatThrownBy(() -> jwtService.parseClaims(tampered)).isInstanceOf(JwtException.class);
	}
}
