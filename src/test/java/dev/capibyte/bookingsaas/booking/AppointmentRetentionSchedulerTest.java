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
 * AppointmentRetentionScheduler (automatic history purge, driven by each tenant's own
 * historyRetentionMonths) and the PATCH /api/tenant/history-retention setting that controls it.
 */
class AppointmentRetentionSchedulerTest extends IntegrationTestBase {

	@Autowired
	private AppointmentRetentionScheduler retentionScheduler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void defaultRetentionIsTwelveMonths() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(response.getBody().get("historyRetentionMonths")).isEqualTo(12);
	}

	@Test
	void rejectsMonthsOutsideOneToTwelve() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> tooHigh = restTemplate.exchange("/api/tenant/history-retention", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("historyRetentionMonths", 13), headers), Map.class);
		assertThat(tooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> tooLow = restTemplate.exchange("/api/tenant/history-retention", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("historyRetentionMonths", 0), headers), Map.class);
		assertThat(tooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void acceptsAValidMonthsValueAndRoundtripsIt() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> patch = restTemplate.exchange("/api/tenant/history-retention", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("historyRetentionMonths", 6), headers), Map.class);
		assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(patch.getBody().get("historyRetentionMonths")).isEqualTo(6);

		ResponseEntity<Map> get = restTemplate.exchange("/api/tenant", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(get.getBody().get("historyRetentionMonths")).isEqualTo(6);
	}

	@Test
	void purgesAppointmentsOlderThanTheTenantsRetentionLimit() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);
		restTemplate.exchange("/api/tenant/history-retention", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("historyRetentionMonths", 1), headers), Void.class);

		String appointmentId = createManual(headers, setup, nextMonday().atTime(9, 0), "old-client");
		backdate(appointmentId, Instant.now().minus(60, ChronoUnit.DAYS));

		retentionScheduler.purgeExpiredHistory();

		ResponseEntity<Map> after = restTemplate.exchange("/api/appointments/" + appointmentId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void keepsAppointmentsWithinTheRetentionLimit() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);
		restTemplate.exchange("/api/tenant/history-retention", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("historyRetentionMonths", 1), headers), Void.class);

		String appointmentId = createManual(headers, setup, nextMonday().atTime(9, 0), "recent-client");
		backdate(appointmentId, Instant.now().minus(15, ChronoUnit.DAYS));

		retentionScheduler.purgeExpiredHistory();

		ResponseEntity<Map> after = restTemplate.exchange("/api/appointments/" + appointmentId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private void backdate(String appointmentId, Instant startTime) {
		jdbcTemplate.update("UPDATE appointments SET start_time = ?, end_time = ? WHERE id = ?::uuid",
				Timestamp.from(startTime), Timestamp.from(startTime.plus(30, ChronoUnit.MINUTES)), appointmentId);
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
