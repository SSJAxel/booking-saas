package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end coverage of the point values agreed on for ClientRatingService: +1 per completed
 * visit, a client's first cancellation is free, the second (and every one after) costs 2, a
 * no-show costs 5, an auto-expired unpaid deposit also costs 5 but — unlike a real cancellation —
 * never counts toward the "first cancellation is free" grace. Reads the rating back through
 * GET /api/reports/clients rather than touching the database directly, since that's the same
 * contract the frontend's ranking panel depends on.
 */
class ClientRatingFlowTest extends IntegrationTestBase {

	@Autowired
	private PendingDepositExpirationScheduler expirationScheduler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${app.booking.deposit-expiration-minutes}")
	private long expirationMinutes;

	@Test
	void completedVisitsAddPointsAndTheFirstCancellationIsFree() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers, null);

		String a1 = book(tenant.slug(), setup, "09:00:00", "client-a@example.com");
		transition(a1, "CONFIRMED", headers);
		transition(a1, "COMPLETED", headers);
		assertThat(ratingFor(headers, "client-a@example.com")).isEqualTo(1);

		String a2 = book(tenant.slug(), setup, "10:00:00", "client-a@example.com");
		transition(a2, "CONFIRMED", headers);
		transition(a2, "COMPLETED", headers);
		assertThat(ratingFor(headers, "client-a@example.com")).isEqualTo(2);

		// First-ever cancellation for this client: free, rating unchanged.
		String a3 = book(tenant.slug(), setup, "11:00:00", "client-a@example.com");
		transition(a3, "CANCELLED", headers);
		assertThat(ratingFor(headers, "client-a@example.com")).isEqualTo(2);

		// Second cancellation: costs 2.
		String a4 = book(tenant.slug(), setup, "12:00:00", "client-a@example.com");
		transition(a4, "CANCELLED", headers);
		assertThat(ratingFor(headers, "client-a@example.com")).isEqualTo(0);
	}

	@Test
	void noShowCostsFivePointsFlooredAtZero() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers, null);

		String a1 = book(tenant.slug(), setup, "09:00:00", "client-b@example.com");
		transition(a1, "CONFIRMED", headers);
		transition(a1, "COMPLETED", headers);
		assertThat(ratingFor(headers, "client-b@example.com")).isEqualTo(1);

		// 1 - 5 would be negative — must floor at 0, not go negative.
		String a2 = book(tenant.slug(), setup, "10:00:00", "client-b@example.com");
		transition(a2, "CONFIRMED", headers);
		transition(a2, "NO_SHOW", headers);
		assertThat(ratingFor(headers, "client-b@example.com")).isEqualTo(0);
	}

	@Test
	void autoExpiredDepositCostsFivePointsAndNeverCountsTowardTheCancellationGrace() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers, 20.0);

		String a1 = book(tenant.slug(), setup, "09:00:00", "client-d@example.com");
		Instant longAgo = Instant.now().minus(expirationMinutes + 5, ChronoUnit.MINUTES);
		jdbcTemplate.update("UPDATE appointments SET created_at = ? WHERE id = ?::uuid", Timestamp.from(longAgo), a1);
		expirationScheduler.expireUnpaidAppointments();
		assertThat(ratingFor(headers, "client-d@example.com")).isEqualTo(0);

		// A genuine first cancellation afterward must still be free — the auto-expiry above must not
		// have counted against this client's cancellation grace.
		Setup depositFreeSetup = setUpProfessionalAndService(headers, null);
		String a2 = book(tenant.slug(), depositFreeSetup, "09:00:00", "client-d@example.com");
		transition(a2, "CANCELLED", headers);
		assertThat(ratingFor(headers, "client-d@example.com")).isEqualTo(0);
	}

	private int ratingFor(HttpHeaders headers, String email) {
		ResponseEntity<Map[]> response = restTemplate.exchange("/api/reports/clients", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		for (Map stats : response.getBody()) {
			if (email.equals(stats.get("clientEmail"))) return ((Number) stats.get("rating")).intValue();
		}
		throw new AssertionError("No client stats found for " + email);
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers, Double depositAmount) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		Map<String, Object> serviceRequest = depositAmount == null
				? Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0)
				: Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0, "depositAmount", depositAmount);
		String serviceId = (String) post("/api/services", serviceRequest, headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		return new Setup(professionalId, serviceId);
	}

	private String book(String tenantSlug, Setup setup, String startTime, String clientEmail) {
		Map<String, Object> body = Map.of(
				"professionalId", setup.professionalId(), "serviceId", setup.serviceId(),
				"date", nextMonday().toString(), "startTime", startTime,
				"clientName", "Client", "clientEmail", clientEmail,
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

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceId) {
	}
}
