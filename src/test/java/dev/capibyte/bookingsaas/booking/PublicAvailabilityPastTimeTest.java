package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Regression test: GET .../availability used to return every open slot for today purely from
 * weekly hours, with no notion of "now" — a client browsing today's date could see (and book) a
 * slot earlier today that had already passed. See PublicAvailabilityService#findFreeSlots.
 */
class PublicAvailabilityPastTimeTest extends IntegrationTestBase {

	@Test
	void todaysAvailabilityExcludesSlotsThatAlreadyPassed() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		// Tenant defaults to UTC (see Tenant.timezone) — using that zone explicitly for "today"/"now"
		// here too, so this test agrees with what PublicAvailabilityService computes regardless of
		// whatever system zone this JVM happens to run in.
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", today.getDayOfWeek().name(), "startTime", "00:00:00", "endTime", "23:00:00"),
				headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		LocalTime beforeRequest = LocalTime.now(ZoneOffset.UTC);

		ResponseEntity<List> response = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/availability?professionalId=" + professionalId + "&serviceId="
						+ serviceId + "&date=" + today,
				List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> slots = response.getBody();
		// The window opens at midnight, so if this were still returning every slot regardless of
		// the clock (the bug), 00:00 would be in the list — it's only absent because the filter
		// compares against "now".
		assertThat(slots).allSatisfy(
				slot -> assertThat(LocalTime.parse((String) slot.get("start")).isBefore(beforeRequest)).isFalse());
	}

	@Test
	void aFutureDatesAvailabilityIsUnaffected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		LocalDate nextWeekSameDay = LocalDate.now(ZoneOffset.UTC).plusDays(7);
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", nextWeekSameDay.getDayOfWeek().name(), "startTime", "09:00:00", "endTime",
						"10:00:00"),
				headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		ResponseEntity<List> response = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/availability?professionalId=" + professionalId + "&serviceId="
						+ serviceId + "&date=" + nextWeekSameDay,
				List.class);

		// A future date has nothing "already passed" — both slots the 09:00-10:00 window produces
		// (30-minute service) must still be there untouched.
		assertThat((List<Map<String, Object>>) response.getBody()).hasSize(2);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
