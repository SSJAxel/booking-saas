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
 * (POST /api/appointments). Reuses AppointmentService.book() under the hood, so the
 * no_double_booking constraint still applies exactly as it does for the public flow. For the
 * intentional-overlap case ("sobreturno"), see AppointmentOvertimeBookingTest; for moving an
 * existing appointment to a new time ("reagendar"), see AppointmentRescheduleFlowTest.
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
