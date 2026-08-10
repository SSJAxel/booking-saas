package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers the owner-side manual booking flow: a manual appointment created straight from the panel
 * (POST /api/appointments), and "replace" (POST /api/appointments/{id}/replace) for when a client
 * cancels by phone instead of through the system. Both reuse AppointmentService.book() under the
 * hood, so the no_double_booking constraint still applies exactly as it does for the public flow —
 * replace() is a cancel-then-book, never a true overlapping booking. For the intentional-overlap
 * case ("sobreturno"), see AppointmentOvertimeBookingTest.
 */
class AppointmentManualBookingTest extends IntegrationTestBase {

	@Test
	void ownerCanCreateAManualAppointment() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> created = createManual(headers, setup, nextMonday().atTime(10, 0), "walk-in-client");

		assertThat(created.get("status")).isEqualTo("CONFIRMED");
		assertThat(created.get("clientEmail")).isEqualTo("walk-in-client@example.com");
	}

	@Test
	void replaceCancelsTheOldAppointmentAndBooksTheNewOneInItsPlace() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> original = createManual(headers, setup, nextMonday().atTime(10, 0), "original-client");
		String originalId = (String) original.get("id");

		Map<String, Object> replacement = replace(headers, originalId, setup, nextMonday().atTime(11, 0),
				"replacement-client");

		assertThat(replacement.get("status")).isEqualTo("CONFIRMED");
		assertThat(replacement.get("clientEmail")).isEqualTo("replacement-client@example.com");

		ResponseEntity<Map> oldAfter = restTemplate.exchange("/api/appointments/" + originalId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(oldAfter.getBody().get("status")).isEqualTo("CANCELLED");
	}

	@Test
	void replaceCanReuseTheExactSlotItJustFreed() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> original = createManual(headers, setup, nextMonday().atTime(10, 0), "original-client");
		String originalId = (String) original.get("id");

		// Same day/time as the original slot — only valid if the cancellation is actually flushed
		// before the replacement's INSERT runs (see AppointmentService.replace's Javadoc).
		Map<String, Object> replacement = replace(headers, originalId, setup, nextMonday().atTime(10, 0),
				"same-slot-client");

		assertThat(replacement.get("status")).isEqualTo("CONFIRMED");
	}

	@Test
	void replaceFailsAndKeepsTheOldAppointmentWhenTheNewSlotIsTaken() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> toReplace = createManual(headers, setup, nextMonday().atTime(10, 0), "to-replace-client");
		String toReplaceId = (String) toReplace.get("id");
		createManual(headers, setup, nextMonday().atTime(11, 0), "other-client");

		Map<String, Object> body = manualBody(setup, nextMonday().atTime(11, 0), "blocked-client");
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + toReplaceId + "/replace",
				HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// The service in setUpProfessionalAndService has no deposit, so a fresh booking auto-confirms
		// (see AppointmentService.book) — the point here is just that it's unchanged by the failed replace.
		ResponseEntity<Map> oldAfter = restTemplate.exchange("/api/appointments/" + toReplaceId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(oldAfter.getBody().get("status")).isEqualTo("CONFIRMED");
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		return new Setup(professionalId, serviceId);
	}

	private Map<String, Object> createManual(HttpHeaders headers, Setup setup, LocalDateTime at,
			String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private Map<String, Object> replace(HttpHeaders headers, String appointmentId, Setup setup,
			LocalDateTime at, String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/replace",
				HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private Map<String, Object> manualBody(Setup setup, LocalDateTime at, String clientHandle) {
		return Map.of(
				"professionalId", setup.professionalId(),
				"serviceId", setup.serviceId(),
				"date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				// Distinct per handle: AppointmentService.findOrCreateClient also matches by phone
				// now, and every clientHandle in this file is meant to represent a different person.
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientHandle.hashCode()) % 100_000_000));
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	// Test tenants stay on the default UTC timezone, and Monday is the only day with availability
	// configured — matches AppointmentBookingFlowTest's convention.
	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceId) {
	}
}
