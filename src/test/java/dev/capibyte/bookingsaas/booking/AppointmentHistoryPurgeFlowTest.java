package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * "Borrar historial" — DELETE /api/appointments/history?window=... — the four Chrome-style windows
 * (last hour / last 24h / last 4 weeks / all) each delete appointments falling WITHIN that recent
 * window, never a future one, and never touch the associated Client's rating/counters.
 */
class AppointmentHistoryPurgeFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void lastHourWindowDeletesOnlyTheThirtyMinuteOldAppointment() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String thirtyMinAgo = createManual(headers, setup, nextMonday().atTime(9, 0), "thirty-min-ago");
		String twoHoursAgo = createManual(headers, setup, nextMonday().atTime(10, 0), "two-hours-ago");
		String twoDaysAgo = createManual(headers, setup, nextMonday().atTime(11, 0), "two-days-ago");
		String future = createManual(headers, setup, nextMonday().atTime(12, 0), "future-client");
		backdate(thirtyMinAgo, Instant.now().minus(30, ChronoUnit.MINUTES));
		backdate(twoHoursAgo, Instant.now().minus(2, ChronoUnit.HOURS));
		backdate(twoDaysAgo, Instant.now().minus(2, ChronoUnit.DAYS));

		Map<String, Object> result = purge(headers, "LAST_HOUR");
		assertThat(((Number) result.get("deleted")).longValue()).isEqualTo(1);

		assertGone(headers, thirtyMinAgo);
		assertExists(headers, twoHoursAgo);
		assertExists(headers, twoDaysAgo);
		assertExists(headers, future);
	}

	@Test
	void last24HoursWindowDeletesUpToADayBack() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String thirtyMinAgo = createManual(headers, setup, nextMonday().atTime(9, 0), "thirty-min-ago");
		String twoHoursAgo = createManual(headers, setup, nextMonday().atTime(10, 0), "two-hours-ago");
		String twoDaysAgo = createManual(headers, setup, nextMonday().atTime(11, 0), "two-days-ago");
		backdate(thirtyMinAgo, Instant.now().minus(30, ChronoUnit.MINUTES));
		backdate(twoHoursAgo, Instant.now().minus(2, ChronoUnit.HOURS));
		backdate(twoDaysAgo, Instant.now().minus(2, ChronoUnit.DAYS));

		Map<String, Object> result = purge(headers, "LAST_24_HOURS");
		assertThat(((Number) result.get("deleted")).longValue()).isEqualTo(2);

		assertGone(headers, thirtyMinAgo);
		assertGone(headers, twoHoursAgo);
		assertExists(headers, twoDaysAgo);
	}

	@Test
	void allWindowDeletesEveryPastAppointmentButNeverAFutureOne() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String thirtyMinAgo = createManual(headers, setup, nextMonday().atTime(9, 0), "thirty-min-ago");
		String twoMonthsAgo = createManual(headers, setup, nextMonday().atTime(10, 0), "two-months-ago");
		String future = createManual(headers, setup, nextMonday().atTime(11, 0), "future-client");
		backdate(thirtyMinAgo, Instant.now().minus(30, ChronoUnit.MINUTES));
		backdate(twoMonthsAgo, Instant.now().minus(60, ChronoUnit.DAYS));

		purge(headers, "ALL");

		assertGone(headers, thirtyMinAgo);
		assertGone(headers, twoMonthsAgo);
		assertExists(headers, future);
	}

	@Test
	void futureAppointmentIsNeverDeletedEvenWithWindowAll() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String future = createManual(headers, setup, nextMonday().atTime(9, 0), "future-only-client");

		purge(headers, "ALL");

		assertExists(headers, future);
	}

	@Test
	void purgingDoesNotTouchClientRatingOrCounters() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String appointmentId = createManual(headers, setup, nextMonday().atTime(9, 0), "rated-client");
		transition(appointmentId, "CONFIRMED", headers);
		transition(appointmentId, "COMPLETED", headers);
		assertThat(clientRating(headers, "rated-client")).isEqualTo(1);
		backdate(appointmentId, Instant.now().minus(30, ChronoUnit.MINUTES));

		purge(headers, "ALL");

		// GET /api/clients/search queries the clients table directly (not derived from
		// appointments like /api/reports/clients is) — the right way to prove the Client row
		// itself survives even though its only appointment is now gone.
		assertThat(clientRating(headers, "rated-client")).isEqualTo(1);
	}

	@Test
	void deletingAnAppointmentWithAnAssociatedSaleNullsTheSaleInsteadOfFailing() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		String appointmentId = createManual(headers, setup, nextMonday().atTime(9, 0), "sale-client");
		String productId = (String) post("/api/products", Map.of("name", "Gel", "price", 10.0, "stock", 5), headers)
				.get("id");
		Map<String, Object> sale = post("/api/sales",
				Map.of("productId", productId, "quantity", 1, "appointmentId", appointmentId), headers);
		String saleId = (String) sale.get("id");
		backdate(appointmentId, Instant.now().minus(30, ChronoUnit.MINUTES));

		Map<String, Object> result = purge(headers, "ALL");
		assertThat(((Number) result.get("deleted")).longValue()).isEqualTo(1);

		Map<String, Object> saleRow = jdbcTemplate.queryForMap(
				"SELECT appointment_id FROM sales WHERE id = ?::uuid", saleId);
		assertThat(saleRow.get("appointment_id")).isNull();
	}

	private void assertGone(HttpHeaders headers, String appointmentId) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private void assertExists(HttpHeaders headers, String appointmentId) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private void backdate(String appointmentId, Instant startTime) {
		jdbcTemplate.update("UPDATE appointments SET start_time = ?, end_time = ? WHERE id = ?::uuid",
				Timestamp.from(startTime), Timestamp.from(startTime.plus(30, ChronoUnit.MINUTES)), appointmentId);
	}

	private Map<String, Object> purge(HttpHeaders headers, String window) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/history?window=" + window,
				HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private int clientRating(HttpHeaders headers, String clientHandle) {
		ResponseEntity<Map[]> response = restTemplate.exchange(
				"/api/clients/search?q=" + clientHandle, HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
		Map[] results = response.getBody();
		assertThat(results).as("client search for " + clientHandle).isNotEmpty();
		return ((Number) results[0].get("rating")).intValue();
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

	private String createManual(HttpHeaders headers, Setup setup, LocalDateTime at, String clientHandle) {
		Map<String, Object> body = Map.of(
				"professionalId", setup.professionalId(),
				"serviceId", setup.serviceId(),
				"date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientHandle.hashCode()) % 100_000_000));
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
