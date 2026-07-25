package dev.capibyte.bookingsaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthFlowTest extends IntegrationTestBase {

	@Test
	void registerThenLoginThenAccessProtectedEndpoint() {
		RegisteredTenant tenant = registerTenant();

		ResponseEntity<Map> me = restTemplate.exchange("/api/me", HttpMethod.GET,
				new HttpEntity<>(authHeaders(tenant.token())), Map.class);

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody().get("role")).isEqualTo("OWNER");
	}

	@Test
	void protectedEndpointWithoutTokenReturns401() {
		ResponseEntity<Map> me = restTemplate.getForEntity("/api/me", Map.class);

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginWithWrongPasswordReturns401() {
		RegisteredTenant tenant = registerTenant();

		Map<String, Object> loginRequest = Map.of(
				"tenantSlug", tenant.slug(),
				"email", tenant.slug() + "@example.com",
				"password", "not-the-real-password");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void registeringTheSameSlugTwiceReturns409() {
		String slug = "dup-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> request = Map.of(
				"tenantName", "First",
				"tenantSlug", slug,
				"ownerEmail", "first-" + slug + "@example.com",
				"ownerPassword", "supersecret123");
		restTemplate.postForEntity("/api/auth/register", request, Map.class);

		Map<String, Object> duplicate = Map.of(
				"tenantName", "Second",
				"tenantSlug", slug,
				"ownerEmail", "second-" + slug + "@example.com",
				"ownerPassword", "supersecret123");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", duplicate, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}
}
