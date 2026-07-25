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

	protected record RegisteredTenant(String slug, String token) {
	}

	protected RegisteredTenant registerTenant() {
		String slug = "t-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> request = Map.of(
				"tenantName", "Test Tenant " + slug,
				"tenantSlug", slug,
				"ownerEmail", slug + "@example.com",
				"ownerPassword", "supersecret123");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);
		String token = (String) response.getBody().get("token");
		return new RegisteredTenant(slug, token);
	}

	protected HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
