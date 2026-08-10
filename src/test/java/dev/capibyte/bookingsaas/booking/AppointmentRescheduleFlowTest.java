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
 * "Reagendar": POST /api/appointments/{id}/reschedule — moves an appointment's date/time in place
 * for the same client/professional/service, and applies the two reschedule rules agreed on:
 * TENANT_DECISION never touches rating and always keeps an already-paid deposit; CLIENT_NOTICE
 * follows the same first-time-free rating grace as a cancellation (its own counter, separate from
 * Client#cancelledCount) and only keeps the deposit the first time.
 */
class AppointmentRescheduleFlowTest extends IntegrationTestBase {

	@Test
	void tenantDecisionMovesTheSlotAndAlwaysKeepsAnAlreadyPaidDeposit() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String appointmentId = bookAndConfirmDeposit(headers, setup, nextMonday().atTime(10, 0), "tenant-client");

		Map<String, Object> rescheduled = reschedule(headers, appointmentId, nextMonday().atTime(14, 0),
				"TENANT_DECISION");

		assertThat(rescheduled.get("paymentStatus")).isEqualTo("PAID");
		assertThat(rescheduled.get("status")).isEqualTo("CONFIRMED");
		assertThat(ratingFor(headers, "tenant-client@example.com")).isEqualTo(0);
	}

	@Test
	void clientNoticeFirstTimeKeepsTheDepositAndDoesNotCostRating() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String appointmentId = bookAndConfirmDeposit(headers, setup, nextMonday().atTime(10, 0), "notice-client");

		Map<String, Object> rescheduled = reschedule(headers, appointmentId, nextMonday().atTime(15, 0),
				"CLIENT_NOTICE");

		assertThat(rescheduled.get("paymentStatus")).isEqualTo("PAID");
		assertThat(ratingFor(headers, "notice-client@example.com")).isEqualTo(0);
	}

	@Test
	void clientNoticeSecondTimeForfeitsTheDepositAndCostsTwoPoints() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		// Give this client a completed-visit point first, so the -2 penalty has something to floor
		// against zero and the assertion below is unambiguous.
		String warmUp = bookAndConfirmDeposit(headers, setup, nextMonday().atTime(9, 0), "twice-client");
		transition(warmUp, "COMPLETED", headers);
		assertThat(ratingFor(headers, "twice-client@example.com")).isEqualTo(1);

		String first = bookAndConfirmDeposit(headers, setup, nextMonday().atTime(10, 0), "twice-client");
		reschedule(headers, first, nextMonday().atTime(11, 0), "CLIENT_NOTICE"); // burns the free first time

		String second = bookAndConfirmDeposit(headers, setup, nextMonday().atTime(12, 0), "twice-client");
		Map<String, Object> rescheduled = reschedule(headers, second, nextMonday().atTime(13, 0), "CLIENT_NOTICE");

		assertThat(rescheduled.get("paymentStatus")).isEqualTo("PENDING");
		assertThat(ratingFor(headers, "twice-client@example.com")).isEqualTo(0); // 1 - 2, floored at 0
	}

	@Test
	void rescheduleToASlotAnotherNormalAppointmentAlreadyHoldsIsBlockedRegression() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String toMove = createManual(headers, setup, nextMonday().atTime(10, 0), "to-move-client");
		createManual(headers, setup, nextMonday().atTime(11, 0), "occupying-client");

		Map<String, Object> body = Map.of("date", nextMonday().toString(), "startTime", "11:00:00",
				"reason", "TENANT_DECISION");
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + toMove + "/reschedule",
				HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// Deposit-required service (see setUpProfessionalAndService), and this appointment's deposit
		// was never confirmed — it started PENDING and the failed reschedule must leave it exactly
		// there, not touch its status at all.
		ResponseEntity<Map> after = restTemplate.exchange("/api/appointments/" + toMove, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(after.getBody().get("status")).isEqualTo("PENDING");
	}

	@Test
	void cannotRescheduleACancelledAppointment() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String appointmentId = createManual(headers, setup, nextMonday().atTime(10, 0), "cancelled-client");
		transition(appointmentId, "CANCELLED", headers);

		Map<String, Object> body = Map.of("date", nextMonday().toString(), "startTime", "12:00:00",
				"reason", "TENANT_DECISION");
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/reschedule",
				HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0, "depositAmount", 20.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		return new Setup(professionalId, serviceId);
	}

	private String createManual(HttpHeaders headers, Setup setup, LocalDateTime at, String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return (String) response.getBody().get("id");
	}

	/** Books (deposit-required service → PENDING) then confirms the deposit (→ PAID/CONFIRMED), so
	 * every reschedule test starts from a real deposit rather than an unpaid one. */
	private String bookAndConfirmDeposit(HttpHeaders headers, Setup setup, LocalDateTime at, String clientHandle) {
		String id = createManual(headers, setup, at, clientHandle);
		restTemplate.exchange("/api/appointments/" + id + "/confirm-deposit", HttpMethod.PATCH,
				new HttpEntity<>(headers), Map.class);
		return id;
	}

	private Map<String, Object> reschedule(HttpHeaders headers, String appointmentId, LocalDateTime at,
			String reason) {
		Map<String, Object> body = Map.of("date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(), "reason", reason);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/reschedule",
				HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private void transition(String appointmentId, String status, HttpHeaders headers) {
		restTemplate.exchange("/api/appointments/" + appointmentId + "/status", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("status", status), headers), Map.class);
	}

	private int ratingFor(HttpHeaders headers, String email) {
		ResponseEntity<Map[]> response = restTemplate.exchange("/api/reports/clients", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		for (Map stats : response.getBody()) {
			if (email.equals(stats.get("clientEmail"))) return ((Number) stats.get("rating")).intValue();
		}
		throw new AssertionError("No client stats found for " + email);
	}

	private Map<String, Object> manualBody(Setup setup, LocalDateTime at, String clientHandle) {
		return Map.of(
				"professionalId", setup.professionalId(),
				"serviceId", setup.serviceId(),
				"date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientHandle.hashCode()) % 100_000_000));
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceId) {
	}
}
