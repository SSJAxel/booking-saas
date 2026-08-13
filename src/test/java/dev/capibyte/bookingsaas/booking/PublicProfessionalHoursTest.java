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
 * PublicProfessionalResponse used to only carry the bio — a "meet the team" hover/card had no way
 * to show a professional's real weekly hours instead. See PublicWeeklyAvailabilityResponse.
 */
class PublicProfessionalHoursTest extends IntegrationTestBase {

	@Test
	void publicProfessionalListingIncludesWeeklyHours() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro", "bio", "Cuts hair"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", "TUESDAY", "startTime", "09:00:00", "endTime", "18:00:00"), headers);

		ResponseEntity<List> response = restTemplate
				.getForEntity("/api/public/" + tenant.slug() + "/professionals", List.class);

		List<Map<String, Object>> professionals = response.getBody();
		Map<String, Object> pro = professionals.stream()
				.filter(p -> professionalId.equals(p.get("id")))
				.findFirst()
				.orElseThrow();
		List<Map<String, Object>> hours = (List<Map<String, Object>>) pro.get("hours");
		assertThat(hours).hasSize(1);
		assertThat(hours.get(0)).containsEntry("dayOfWeek", "TUESDAY");
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
