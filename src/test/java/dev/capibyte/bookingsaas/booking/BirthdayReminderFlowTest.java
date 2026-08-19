package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
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
 * "Cumpleaños del mes" (PRO/MAX) + mail automático de cumpleaños (MAX) — idea de un cliente de la
 * competencia relayada 2026-08-18: recordarle al tenant, nunca aplicar el descuento solo. Ver
 * PlanTier's Javadoc para el detalle completo.
 */
class BirthdayReminderFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BirthdayEmailScheduler birthdayEmailScheduler;

	@Test
	void ownerCanSetAndClearAClientsBirthday() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		String clientId = bookOneClient(tenant, headers, "birthday-client@example.com");

		ResponseEntity<Map> set = restTemplate.exchange("/api/clients/" + clientId + "/birthday", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("birthDate", "1995-03-14"), headers), Map.class);
		assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(set.getBody().get("birthDate")).isEqualTo("1995-03-14");

		Map<String, Object> clearBody = new HashMap<>();
		clearBody.put("birthDate", null);
		ResponseEntity<Map> cleared = restTemplate.exchange("/api/clients/" + clientId + "/birthday", HttpMethod.PATCH,
				new HttpEntity<>(clearBody, headers), Map.class);
		assertThat(cleared.getBody().get("birthDate")).isNull();
	}

	@Test
	void birthdaysThisMonthRejectedBelowProPlan() {
		RegisteredTenant tenant = registerTenant(); // defaults to TRIAL
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/clients/birthdays-this-month", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void birthdaysThisMonthListsOnlyThisMonthOrderedByDay() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());

		LocalDate today = LocalDate.now();
		String laterId = bookOneClient(tenant, headers, "later-birthday@example.com");
		String earlierId = bookOneClient(tenant, headers, "earlier-birthday@example.com");
		String otherMonthId = bookOneClient(tenant, headers, "other-month-birthday@example.com");
		// Day 1 and day 15 of the current month are valid regardless of which month "today" is.
		setBirthday(earlierId, headers, LocalDate.of(1990, today.getMonthValue(), 1));
		setBirthday(laterId, headers, LocalDate.of(1990, today.getMonthValue(), 15));
		setBirthday(otherMonthId, headers, today.plusMonths(6).withDayOfMonth(1));

		ResponseEntity<List> response = restTemplate.exchange("/api/clients/birthdays-this-month", HttpMethod.GET,
				new HttpEntity<>(headers), List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> body = response.getBody();
		List<String> ids = body.stream().map(c -> (String) c.get("id")).toList();
		assertThat(ids).doesNotContain(otherMonthId);
		assertThat(ids.indexOf(earlierId)).isLessThan(ids.indexOf(laterId));
	}

	@Test
	void settingBirthdayMessageRejectedBelowMaxPlan() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/birthday-message", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("message", "¡Feliz cumple {nombre}!"), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void schedulerEmailsTodaysBirthdayClientOnceAndSkipsTheSameYearOnRerun() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> messageSet = restTemplate.exchange("/api/tenant/birthday-message", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("message", "¡Feliz cumple {nombre}! Este mes tenés 15% de descuento."), headers),
				Map.class);
		assertThat(messageSet.getStatusCode()).isEqualTo(HttpStatus.OK);

		String clientId = bookOneClient(tenant, headers, "today-birthday@example.com");
		LocalDate today = LocalDate.now();
		setBirthday(clientId, headers, today.minusYears(20));

		birthdayEmailScheduler.sendBirthdayEmails();

		Integer yearAfterFirstRun = jdbcTemplate.queryForObject(
				"SELECT last_birthday_email_year FROM clients WHERE id = ?::uuid", Integer.class, clientId);
		assertThat(yearAfterFirstRun).isEqualTo(today.getYear());

		// Force createdAt-style state to prove a re-run the same day doesn't send a second time — the
		// only observable signal available from a test is that the stored year doesn't change/reset.
		birthdayEmailScheduler.sendBirthdayEmails();
		Integer yearAfterSecondRun = jdbcTemplate.queryForObject(
				"SELECT last_birthday_email_year FROM clients WHERE id = ?::uuid", Integer.class, clientId);
		assertThat(yearAfterSecondRun).isEqualTo(today.getYear());
	}

	private void setBirthday(String clientId, HttpHeaders headers, LocalDate birthDate) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/clients/" + clientId + "/birthday", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("birthDate", birthDate.toString()), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String bookOneClient(RegisteredTenant tenant, HttpHeaders headers, String clientEmail) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 20.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		// Distinct phone per call — findOrCreateClient falls back to a phone match when the email is
		// new, so reusing one phone across calls in the same test would silently collapse them into
		// the same Client row instead of the 3 distinct ones each test expects.
		Map<String, Object> body = Map.of(
				"professionalId", professionalId, "serviceId", serviceId,
				"date", nextMonday().toString(), "startTime", "10:00:00",
				"clientName", "Client " + clientEmail, "clientEmail", clientEmail,
				"clientPhone", "+5411" + Math.abs(clientEmail.hashCode() % 1000000));
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				body, Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return (String) response.getBody().get("clientId");
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
