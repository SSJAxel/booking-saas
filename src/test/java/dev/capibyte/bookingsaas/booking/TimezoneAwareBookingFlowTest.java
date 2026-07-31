package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The regression test for the original bug: appointment times used to be treated as UTC
 * wall-clock end to end, so a tenant outside UTC would have every booking silently land at the
 * wrong actual time. Buenos Aires (UTC-3, no DST) is a stable, unambiguous fixture zone.
 */
class TimezoneAwareBookingFlowTest extends IntegrationTestBase {

	private static final String BUENOS_AIRES = "America/Argentina/Buenos_Aires";

	@Test
	void bookingAtALocalTimeStoresTheCorrectAbsoluteInstant() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> tzResponse = restTemplate.exchange("/api/tenant/timezone", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("timezone", BUENOS_AIRES), headers), Map.class);
		assertThat(tzResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(tzResponse.getBody().get("timezone")).isEqualTo(BUENOS_AIRES);

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 60, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		LocalDate monday = nextMonday();

		// The availability search itself works purely in local wall-clock terms, unaffected by the
		// bug — this just confirms the fixture (9-18 local) is set up as expected before booking.
		ResponseEntity<List> availabilityBefore = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/availability?professionalId=" + professionalId + "&serviceId="
						+ serviceId + "&date=" + monday,
				List.class);
		assertThat((List<Map<String, Object>>) availabilityBefore.getBody())
				.anySatisfy(slot -> assertThat(slot.get("start")).isEqualTo("10:00:00"));

		Map<String, Object> bookingBody = Map.of(
				"professionalId", professionalId, "serviceId", serviceId, "date", monday.toString(), "startTime",
				"10:00:00", "clientName", "Cliente", "clientEmail", "cliente@example.com");
		ResponseEntity<Map> booking = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				bookingBody, Map.class);
		assertThat(booking.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 10:00 in Buenos Aires (UTC-3, no DST) is 13:00 UTC. Before the fix this would have been
		// stored as 10:00 UTC instead — 3 hours off, i.e. 7am local.
		Instant expected = monday.atTime(13, 0).atZone(java.time.ZoneOffset.UTC).toInstant();
		assertThat(Instant.parse((String) booking.getBody().get("startTime"))).isEqualTo(expected);

		// And the slot the booking actually occupies (10:00-11:00 local) no longer shows up as free —
		// exercises findActiveByProfessionalAndDay's zone-aware day boundary, not just book()'s.
		ResponseEntity<List> availabilityAfter = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/availability?professionalId=" + professionalId + "&serviceId="
						+ serviceId + "&date=" + monday,
				List.class);
		assertThat((List<Map<String, Object>>) availabilityAfter.getBody())
				.noneSatisfy(slot -> assertThat(slot.get("start")).isEqualTo("10:00:00"));
	}

	@Test
	void rejectsAnUnknownTimezone() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/timezone", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("timezone", "Not/AZone"), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}
}
