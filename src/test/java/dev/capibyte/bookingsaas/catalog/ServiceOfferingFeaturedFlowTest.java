package dev.capibyte.bookingsaas.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@code featured} is a nullable Boolean on the wire, not a primitive — a record's primitive
 * constructor param must be present in every request body or Jackson rejects the whole request
 * (hit this exact regression: every existing test that created/updated a service without knowing
 * about "featured" started failing with MismatchedInputException once it was added as a bare
 * boolean). Boxed, it's genuinely optional: omitted on create defaults to false, omitted on
 * update leaves the current value untouched instead of silently resetting it.
 */
class ServiceOfferingFeaturedFlowTest extends IntegrationTestBase {

	@Test
	void creatingWithoutFeaturedDefaultsToFalse() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		Map created = post("/api/services", Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers);

		assertThat(created.get("featured")).isEqualTo(false);
	}

	@Test
	void creatingWithFeaturedTrueIsRespected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		Map created = post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0, "featured", true), headers);

		assertThat(created.get("featured")).isEqualTo(true);
	}

	@Test
	void updatingWithoutFeaturedPreservesTheCurrentValue() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		Map created = post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0, "featured", true), headers);
		String serviceId = (String) created.get("id");

		ResponseEntity<Map> updated = restTemplate.exchange("/api/services/" + serviceId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Cut renamed", "durationMinutes", 30, "price", 50.0, "active", true),
						headers),
				Map.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("featured")).isEqualTo(true);
	}

	@Test
	void publicCatalogExposesFeatured() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		post("/api/services", Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0, "featured", true), headers);

		ResponseEntity<List> response = restTemplate.getForEntity("/api/public/" + tenant.slug() + "/services",
				List.class);

		List<Map<String, Object>> services = response.getBody();
		assertThat(services).hasSize(1);
		assertThat(services.get(0).get("featured")).isEqualTo(true);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}
}
