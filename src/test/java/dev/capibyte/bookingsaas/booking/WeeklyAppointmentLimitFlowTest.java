package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * PlanTier.PERSONAL is the only tier with a weekly-appointment cap. Books through
 * POST /api/appointments/overtime (not the plain manual endpoint) on purpose: overtime bypasses
 * the no_double_booking EXCLUDE constraint entirely, so the same professional/slot can be reused
 * 20+ times without fighting an unrelated conflict — the strictest possible exercise of the weekly
 * limit itself, isolated from any other booking rule.
 */
class WeeklyAppointmentLimitFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void personalPlanRejectsThe21stAppointmentInTheSameCalendarWeek() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());
		Setup setup = setUpProfessionalAndService(headers);

		for (int i = 0; i < 20; i++) {
			createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-" + i);
		}

		ResponseEntity<Map> twentyFirst = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
				new HttpEntity<>(manualBody(setup, nextMonday().atTime(10, 0), "client-20"), headers), Map.class);
		assertThat(twentyFirst.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void personalPlanAllowsBookingInTheFollowingWeekEvenWhenThisWeekIsAtTheCap() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());
		Setup setup = setUpProfessionalAndService(headers);

		for (int i = 0; i < 20; i++) {
			createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-" + i);
		}

		ResponseEntity<Map> nextWeek = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
				new HttpEntity<>(manualBody(setup, nextMonday().plusWeeks(1).atTime(10, 0), "client-next-week"),
						headers),
				Map.class);
		assertThat(nextWeek.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void trialPlanHasNoWeeklyLimit() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default — no need to set the tier here.
		Setup setup = setUpProfessionalAndService(headers);

		for (int i = 0; i < 21; i++) {
			createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-" + i);
		}
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

	private Map<String, Object> createOvertime(HttpHeaders headers, Setup setup, LocalDateTime at,
			String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
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
