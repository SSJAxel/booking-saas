package dev.capibyte.bookingsaas;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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

	// Spring's test context caching keeps this same TestRestTemplate (and its underlying bucket
	// cache in PublicApiRateLimitFilter) alive across every one of the ~50 test classes in the
	// suite, all calling /api/public/** as the same loopback "IP" — a full sequential run's
	// cumulative volume can exhaust that one shared bucket, 429ing a test that has nothing to do
	// with rate limiting. Fix: every test gets its own fake client IP (the filter already prefers
	// X-Forwarded-For over the socket address), so each gets its own fresh bucket — same behavior
	// PublicApiRateLimitFilter has in production for two different real clients, nothing weakened.
	private static final AtomicInteger clientIpCounter = new AtomicInteger();
	private static final AtomicBoolean rateLimitIsolationInstalled = new AtomicBoolean(false);
	private static final ThreadLocal<String> currentTestClientIp = new ThreadLocal<>();

	@PostConstruct
	void installPerTestClientIpInterceptor() {
		if (rateLimitIsolationInstalled.compareAndSet(false, true)) {
			restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
				String ip = currentTestClientIp.get();
				if (ip != null) {
					request.getHeaders().add("X-Forwarded-For", ip);
				}
				return execution.execute(request, body);
			});
		}
	}

	@BeforeEach
	void assignUniqueClientIpForRateLimiting() {
		int n = clientIpCounter.incrementAndGet();
		currentTestClientIp.set("10.%d.%d.%d".formatted((n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF));
	}

	protected record RegisteredTenant(String slug, String token) {
	}

	/**
	 * Registering no longer returns a usable token (see AuthService — email verification gates
	 * login now). Tests can't click an emailed link, so this marks the owner verified directly in
	 * the database — the same "bypass the external dependency, not the behavior under test"
	 * pattern already used elsewhere for MercadoPago-gated plan changes — then logs in for real.
	 * Also auto-approves the tenant (see TenantStatus#PENDING_APPROVAL) the same way, so every
	 * existing test that registers a tenant and immediately books against its public site doesn't
	 * need to know about the approval gate. Tests that specifically exercise that gate (pending vs.
	 * approved) skip this helper and drive registration/approval themselves.
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
		jdbcTemplate.update("UPDATE tenants SET status = 'ACTIVE' WHERE slug = ?", slug);

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
