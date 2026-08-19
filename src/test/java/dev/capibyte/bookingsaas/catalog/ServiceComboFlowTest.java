package dev.capibyte.bookingsaas.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
 * Covers service combo pricing (MAX plan only) — a tenant-defined combined price/deposit for a
 * specific pair of services booked together, see README "IDEA A FUTURO" → precio de combo.
 */
class ServiceComboFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void creatingAComboIsRejectedBelowMaxPlan() {
		RegisteredTenant tenant = registerTenant(); // defaults to TRIAL
		HttpHeaders headers = authHeaders(tenant.token());
		Setup pair = setUpTwoServices(headers);

		ResponseEntity<Map> response = restTemplate.exchange("/api/service-combos", HttpMethod.POST,
				new HttpEntity<>(comboBody(pair, 105.0, 20.0), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void bookingTheExactComboPairChargesTheCombinedDepositOnlyOnce() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());
		Setup pair = setUpTwoServices(headers);

		Map created = post("/api/service-combos", comboBody(pair, 105.0, 20.0), headers);
		assertThat(created.get("comboPrice")).isEqualTo(105.0);

		// The public preview endpoint should agree before any booking happens.
		ResponseEntity<Map> preview = restTemplate.exchange(
				"/api/public/" + tenant.slug() + "/service-combo?serviceAId=" + pair.serviceAId() + "&serviceBId="
						+ pair.serviceBId(),
				HttpMethod.GET, HttpEntity.EMPTY, Map.class);
		assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(preview.getBody().get("comboDepositAmount")).isEqualTo(20.0);

		LocalDate monday = nextMonday();
		Map<String, Object> body = Map.of(
				"clientName", "Cliente Combo",
				"clientEmail", "combo-deposit-client@example.com",
				"clientPhone", "+5411000096",
				"items", List.of(
						item(pair.serviceAId(), pair.professionalId(), monday, "10:00:00"),
						item(pair.serviceBId(), pair.professionalId(), monday, "12:00:00")));

		ResponseEntity<List> response = restTemplate
				.postForEntity("/api/public/" + tenant.slug() + "/appointments/group", body, List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Map<String, Object>> appointments = response.getBody();
		assertThat(appointments).hasSize(2);
		assertThat(appointments.get(0).get("paymentStatus")).isEqualTo("PENDING");
		assertThat(((Number) appointments.get(0).get("depositAmountOverride")).doubleValue()).isEqualTo(20.0);
		// The 2nd leg's own service has no individual depositAmount configured (see setUpTwoServices)
		// so without the combo it would already be NOT_REQUIRED — the real assertion is that it never
		// got its own separate charge layered on top of the first leg's combined one.
		assertThat(appointments.get(1).get("paymentStatus")).isEqualTo("NOT_REQUIRED");
	}

	@Test
	void bookingOnlyOneOfTheTwoComboServicesGetsNoDiscount() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());
		Setup pair = setUpTwoServices(headers);
		post("/api/service-combos", comboBody(pair, 105.0, 20.0), headers);

		LocalDate monday = nextMonday();
		Map<String, Object> body = Map.of(
				"professionalId", pair.professionalId(), "serviceId", pair.serviceAId(),
				"date", monday.toString(), "startTime", "10:00:00",
				"clientName", "Solo un servicio", "clientEmail", "solo-client@example.com",
				"clientPhone", "+5411000095");

		ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				body, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		// serviceA has no depositAmount of its own (see setUpTwoServices) — booked alone, no combo
		// ever applies, so it must confirm immediately with nothing to pay.
		assertThat(response.getBody().get("paymentStatus")).isEqualTo("NOT_REQUIRED");
		assertThat(response.getBody().get("depositAmountOverride")).isNull();
	}

	private Map<String, Object> comboBody(Setup pair, double comboPrice, double comboDepositAmount) {
		return Map.of("serviceAId", pair.serviceAId(), "serviceBId", pair.serviceBId(), "comboPrice", comboPrice,
				"comboDepositAmount", comboDepositAmount);
	}

	private Map<String, Object> item(String serviceId, String professionalId, LocalDate date, String startTime) {
		return Map.of("professionalId", professionalId, "serviceId", serviceId, "date", date.toString(), "startTime",
				startTime);
	}

	/** One professional offering two services, neither with its own depositAmount — any deposit seen
	 * in a test only came from the combo, never accidentally from the service's own config. */
	private Setup setUpTwoServices(HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Ink Artist"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);

		String serviceAId = (String) post("/api/services",
				Map.of("name", "Tatuaje", "durationMinutes", 60, "price", 90.0), headers).get("id");
		String serviceBId = (String) post("/api/services",
				Map.of("name", "Piercing", "durationMinutes", 30, "price", 30.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceAId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		restTemplate.exchange("/api/services/" + serviceBId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		return new Setup(professionalId, serviceAId, serviceBId);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceAId, String serviceBId) {
	}
}
