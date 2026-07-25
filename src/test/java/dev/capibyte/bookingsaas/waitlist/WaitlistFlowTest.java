package dev.capibyte.bookingsaas.waitlist;

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

/** Proves real FIFO semantics: only the oldest WAITING entry gets notified per freed slot. */
class WaitlistFlowTest extends IntegrationTestBase {

	@Test
	void cancellingAnAppointmentNotifiesOnlyTheOldestWaitlistEntry() {
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

		String date = "2026-08-17"; // a Monday
		String startTime = date + "T10:00:00Z";

		Map<String, Object> bookingBody = Map.of(
				"professionalId", professionalId, "serviceId", serviceId, "startTime", startTime,
				"clientName", "First Client", "clientEmail", "first@example.com", "clientPhone", "+541100000001");
		ResponseEntity<Map> booking = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				bookingBody, Map.class);
		String appointmentId = (String) booking.getBody().get("id");

		// Two clients join the waitlist for that date, A before B.
		joinWaitlist(tenant.slug(), professionalId, serviceId, date, "Waiter A", "waiter-a@example.com");
		joinWaitlist(tenant.slug(), professionalId, serviceId, date, "Waiter B", "waiter-b@example.com");

		restTemplate.exchange("/api/appointments/" + appointmentId + "/status", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("status", "CANCELLED"), headers), Map.class);

		ResponseEntity<List> entries = restTemplate.exchange("/api/waitlist?professionalId=" + professionalId,
				HttpMethod.GET, new HttpEntity<>(headers), List.class);

		List<Map<String, Object>> body = entries.getBody();
		assertThat(body).hasSize(2);
		Map<String, Object> waiterA = findByEmail(body, "waiter-a@example.com");
		Map<String, Object> waiterB = findByEmail(body, "waiter-b@example.com");

		assertThat(waiterA.get("status")).isEqualTo("NOTIFIED");
		assertThat(waiterB.get("status")).isEqualTo("WAITING");
	}

	private void joinWaitlist(String tenantSlug, String professionalId, String serviceId, String date, String name,
			String email) {
		Map<String, Object> body = Map.of(
				"professionalId", professionalId, "serviceId", serviceId, "date", date,
				"clientName", name, "clientEmail", email);
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/" + tenantSlug + "/waitlist", body,
				Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private Map<String, Object> findByEmail(List<Map<String, Object>> entries, String email) {
		return entries.stream().filter(e -> email.equals(e.get("clientEmail"))).findFirst()
				.orElseThrow(() -> new AssertionError("No waitlist entry for " + email));
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
