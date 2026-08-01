package dev.capibyte.bookingsaas;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full-stack HTTP tests against a real embedded server + real Postgres (Testcontainers) — needed
 * because the guarantees under test (tenant isolation via Hibernate @TenantId, the double-booking
 * EXCLUDE constraint) only hold end to end, not against mocks, and the concurrency test needs a
 * real server to fire genuinely simultaneous requests against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	protected record RegisteredTenant(String slug, String token) {
	}

	/**
	 * Registering no longer returns a usable token (see AuthService — email verification gates
	 * login now). Tests can't click an emailed link, so this marks the owner verified directly in
	 * the database — the same "bypass the external dependency, not the behavior under test"
	 * pattern already used elsewhere for MercadoPago-gated plan changes — then logs in for real.
	 */
	protected RegisteredTenant registerTenant() {
		String slug = "t-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		String password = "supersecret123";
		Map<String, Object> request = Map.of(
				"tenantName", "Test Tenant " + slug,
				"tenantSlug", slug,
				"ownerEmail", email,
				"ownerPassword", password);
		restTemplate.postForEntity("/api/auth/register", request, Map.class);

		jdbcTemplate.update("UPDATE app_users SET email_verified = true WHERE email = ?", email);

		Map<String, Object> loginRequest = Map.of("tenantSlug", slug, "email", email, "password", password);
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		String token = (String) loginResponse.getBody().get("token");
		return new RegisteredTenant(slug, token);
	}

	protected HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
