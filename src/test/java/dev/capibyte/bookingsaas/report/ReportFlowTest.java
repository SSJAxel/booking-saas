package dev.capibyte.bookingsaas.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class ReportFlowTest extends IntegrationTestBase {

	@Test
	void summaryAggregatesRevenueAndNoShowRateCorrectly() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 60, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		// A deposit-requiring service, so this appointment has something to wait for and stays
		// PENDING instead of auto-confirming (that's what a no-deposit booking does now).
		String depositServiceId = (String) post("/api/services",
				Map.of("name", "Color", "durationMinutes", 60, "price", 80.0, "depositAmount", 20.0), headers).get("id");
		restTemplate.exchange("/api/services/" + depositServiceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		String appt1 = bookAppointment(tenant.slug(), professionalId, serviceId, "10:00:00", "a@example.com");
		String appt2 = bookAppointment(tenant.slug(), professionalId, serviceId, "11:00:00", "b@example.com");
		bookAppointment(tenant.slug(), professionalId, depositServiceId, "12:00:00", "c@example.com"); // stays PENDING, awaiting deposit

		transition(appt1, "CONFIRMED", headers);
		transition(appt1, "COMPLETED", headers);
		transition(appt2, "CONFIRMED", headers);
		transition(appt2, "NO_SHOW", headers);

		ResponseEntity<Map> response = restTemplate.exchange("/api/reports/summary", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);

		Map<String, Object> body = response.getBody();
		assertThat(((Number) body.get("totalAppointments")).intValue()).isEqualTo(3);
		assertThat(new java.math.BigDecimal(body.get("revenue").toString())).isEqualByComparingTo("50.00");
		assertThat(((Number) body.get("noShowRate")).doubleValue()).isEqualTo(0.5);

		Map<String, Object> byStatus = (Map<String, Object>) body.get("byStatus");
		assertThat(((Number) byStatus.get("PENDING")).intValue()).isEqualTo(1);
		assertThat(((Number) byStatus.get("COMPLETED")).intValue()).isEqualTo(1);
		assertThat(((Number) byStatus.get("NO_SHOW")).intValue()).isEqualTo(1);
	}

	@Test
	void clientStatsCountsCompletedAndCancelledPerClient() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		// repeat-client: books twice, completes both — the "viene seguido" signal.
		String repeat1 = bookAppointment(tenant.slug(), professionalId, serviceId, "09:00:00", "repeat@example.com");
		String repeat2 = bookAppointment(tenant.slug(), professionalId, serviceId, "10:00:00", "repeat@example.com");
		transition(repeat1, "CONFIRMED", headers);
		transition(repeat1, "COMPLETED", headers);
		transition(repeat2, "CONFIRMED", headers);
		transition(repeat2, "COMPLETED", headers);

		// canceller: books once, cancels — the "cancela más" signal.
		String cancelled = bookAppointment(tenant.slug(), professionalId, serviceId, "11:00:00", "canceller@example.com");
		transition(cancelled, "CANCELLED", headers);

		ResponseEntity<Map[]> response = restTemplate.exchange("/api/reports/clients", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		Map[] body = response.getBody();
		assertThat(body).hasSize(2);

		Map repeatStats = findByEmail(body, "repeat@example.com");
		assertThat(((Number) repeatStats.get("totalAppointments")).intValue()).isEqualTo(2);
		assertThat(((Number) repeatStats.get("completedCount")).intValue()).isEqualTo(2);
		assertThat(((Number) repeatStats.get("cancelledCount")).intValue()).isEqualTo(0);

		Map cancellerStats = findByEmail(body, "canceller@example.com");
		assertThat(((Number) cancellerStats.get("totalAppointments")).intValue()).isEqualTo(1);
		assertThat(((Number) cancellerStats.get("cancelledCount")).intValue()).isEqualTo(1);
		assertThat(((Number) cancellerStats.get("completedCount")).intValue()).isEqualTo(0);

		// Sorted by totalAppointments descending: the repeat client (2) comes before the canceller (1).
		assertThat(body[0].get("clientEmail")).isEqualTo("repeat@example.com");
	}

	private Map findByEmail(Map[] clientStats, String email) {
		for (Map stats : clientStats) {
			if (email.equals(stats.get("clientEmail"))) return stats;
		}
		throw new AssertionError("No client stats found for " + email);
	}

	private String bookAppointment(String tenantSlug, String professionalId, String serviceId, String startTime,
			String clientEmail) {
		Map<String, Object> body = Map.of(
				"professionalId", professionalId, "serviceId", serviceId, "date", "2026-08-17", "startTime", startTime,
				"clientName", "Client", "clientEmail", clientEmail,
				// Derived from the email, not a shared literal: AppointmentService.findOrCreateClient
				// also matches by phone now, and every distinct clientEmail here represents a
				// different person, including repeated calls with the *same* email in the "repeat
				// client" test case below, which must resolve to the same phone too.
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientEmail.hashCode()) % 100_000_000));
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/" + tenantSlug + "/appointments", body,
				Map.class);
		return (String) response.getBody().get("id");
	}

	private void transition(String appointmentId, String status, HttpHeaders headers) {
		restTemplate.exchange("/api/appointments/" + appointmentId + "/status", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("status", status), headers), Map.class);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
