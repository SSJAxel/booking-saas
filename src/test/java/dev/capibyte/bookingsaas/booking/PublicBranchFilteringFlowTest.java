package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * A single-branch tenant (still the common case) never passes branchId and sees every service —
 * this only starts filtering once a client actually picks a branch, which is what makes it
 * backward compatible for every tenant that existed before this feature.
 */
class PublicBranchFilteringFlowTest extends IntegrationTestBase {

	@Test
	void servicesAndProfessionalsCanBeFilteredByBranch() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchA = (String) post("/api/branches", Map.of("name", "Centro"), headers).get("id");
		String branchB = (String) post("/api/branches", Map.of("name", "Norte"), headers).get("id");

		String proA = (String) post("/api/professionals", Map.of("branchId", branchA, "displayName", "Pro A"), headers)
				.get("id");
		String proB = (String) post("/api/professionals", Map.of("branchId", branchB, "displayName", "Pro B"), headers)
				.get("id");

		String serviceX = (String) post("/api/services",
				Map.of("name", "Corte", "durationMinutes", 30, "price", 10.0), headers).get("id");
		String serviceY = (String) post("/api/services",
				Map.of("name", "Color", "durationMinutes", 60, "price", 20.0), headers).get("id");

		assign(serviceX, proA, headers);
		assign(serviceY, proB, headers);

		// GET .../branches
		ResponseEntity<List> branches = restTemplate.getForEntity("/api/public/" + tenant.slug() + "/branches",
				List.class);
		assertThat((List<Map<String, Object>>) branches.getBody()).extracting(b -> b.get("name"))
				.containsExactlyInAnyOrder("Centro", "Norte");

		// No branchId — both services visible, same as a single-branch tenant sees today.
		ResponseEntity<List> allServices = restTemplate.getForEntity("/api/public/" + tenant.slug() + "/services",
				List.class);
		assertThat((List<Map<String, Object>>) allServices.getBody()).extracting(s -> s.get("name"))
				.containsExactlyInAnyOrder("Corte", "Color");

		// Filtered by branch A — only the service offered there.
		ResponseEntity<List> servicesAtA = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/services?branchId=" + branchA, List.class);
		assertThat((List<Map<String, Object>>) servicesAtA.getBody()).extracting(s -> s.get("name"))
				.containsExactly("Corte");

		// Professional filtering: Pro A offers "Corte" at branch A, but not at branch B.
		ResponseEntity<List> prosAtA = restTemplate.getForEntity("/api/public/" + tenant.slug() + "/professionals?serviceId="
				+ serviceX + "&branchId=" + branchA, List.class);
		assertThat((List<Map<String, Object>>) prosAtA.getBody()).extracting(p -> p.get("displayName"))
				.containsExactly("Pro A");

		ResponseEntity<List> prosAtBForServiceX = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/professionals?serviceId=" + serviceX + "&branchId=" + branchB,
				List.class);
		assertThat((List<Map<String, Object>>) prosAtBForServiceX.getBody()).isEmpty();
	}

	private void assign(String serviceId, String professionalId, HttpHeaders headers) {
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
