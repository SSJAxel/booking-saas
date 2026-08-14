package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * DateAvailability opens a specific date regardless of its day of week — the case that motivated
 * it is a professional who works by season and never commits to a recurring weekly pattern at
 * all, so this test deliberately never posts to .../availability (the recurring endpoint).
 */
class DateAvailabilityFlowTest extends IntegrationTestBase {

	@Test
	void aDateWithNoWeeklyAvailabilityIsBookableOnceOpenedAsADateAvailability() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Seasonal Pro"), headers).get("id");
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		LocalDate targetDate = nextDateNotOn(DayOfWeek.MONDAY);

		// Nothing open yet — no weekly pattern was ever configured for this professional.
		assertThat(fetchSlots(tenant.slug(), professionalId, serviceId, targetDate)).isEmpty();

		Map dateAvailability = post("/api/professionals/" + professionalId + "/date-availability",
				Map.of("date", targetDate.toString(), "startTime", "09:00:00", "endTime", "10:00:00"), headers);

		List<Map<String, Object>> slots = fetchSlots(tenant.slug(), professionalId, serviceId, targetDate);
		assertThat(slots).hasSize(2); // 09:00-09:30 and 09:30-10:00, 30-minute service

		String dateAvailabilityId = (String) dateAvailability.get("id");
		restTemplate.exchange(
				"/api/professionals/" + professionalId + "/date-availability/" + dateAvailabilityId,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

		assertThat(fetchSlots(tenant.slug(), professionalId, serviceId, targetDate)).isEmpty();
	}

	@Test
	void dateAvailabilityAddsOnTopOfAnExistingWeeklyPatternRatherThanReplacingIt() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		LocalDate targetDate = nextDateNotOn(DayOfWeek.MONDAY);
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", targetDate.getDayOfWeek().name(), "startTime", "09:00:00", "endTime", "09:30:00"),
				headers);
		post("/api/professionals/" + professionalId + "/date-availability",
				Map.of("date", targetDate.toString(), "startTime", "14:00:00", "endTime", "14:30:00"), headers);

		List<Map<String, Object>> slots = fetchSlots(tenant.slug(), professionalId, serviceId, targetDate);

		// Both the recurring 09:00 window and the one-off 14:00 window contribute a slot — neither
		// source replaces the other.
		assertThat(slots).extracting(s -> LocalTime.parse((String) s.get("start")))
				.containsExactlyInAnyOrder(LocalTime.of(9, 0), LocalTime.of(14, 0));
	}

	private LocalDate nextDateNotOn(DayOfWeek excluded) {
		LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(14);
		while (date.getDayOfWeek() == excluded) {
			date = date.plusDays(1);
		}
		return date;
	}

	private List<Map<String, Object>> fetchSlots(String slug, String professionalId, String serviceId, LocalDate date) {
		ResponseEntity<List> response = restTemplate.getForEntity(
				"/api/public/" + slug + "/availability?professionalId=" + professionalId + "&serviceId=" + serviceId
						+ "&date=" + date,
				List.class);
		return response.getBody();
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
