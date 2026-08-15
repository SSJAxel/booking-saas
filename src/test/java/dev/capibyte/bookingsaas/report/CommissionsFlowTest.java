package dev.capibyte.bookingsaas.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * Staff commissions: PRO/MAX-gated, opt-in per tenant, two separate rates per professional
 * (service/product), computed live over completed appointments and recorded sales. Books through
 * /api/appointments/overtime, same trick LoyaltyRewardsFlowTest/WeeklyAppointmentLimitFlowTest
 * use, so the same professional/slot can be reused across tests without fighting the
 * no_double_booking constraint.
 */
class CommissionsFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void enablingCommissionsIsRejectedOnPlansWithoutTheFeatureAndAllowedOnProAndMax() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// TRIAL by default.
		ResponseEntity<Map> onTrial = updateCommissions(headers, true);
		assertThat(onTrial.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		setPlanTier(tenant, "PRO");
		ResponseEntity<Map> onPro = updateCommissions(headers, true);
		assertThat(onPro.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(onPro.getBody().get("commissionsEnabled")).isEqualTo(true);
	}

	@Test
	void settingRatesOnAProfessionalPersistsThem() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String proId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Ana"), headers).get("id");

		ResponseEntity<Map> updated = restTemplate.exchange("/api/professionals/" + proId, HttpMethod.PUT,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Ana", "active", true,
						"serviceCommissionRate", 40.0, "productCommissionRate", 10.0), headers),
				Map.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new BigDecimal(updated.getBody().get("serviceCommissionRate").toString()))
				.isEqualByComparingTo("40.00");
		assertThat(new BigDecimal(updated.getBody().get("productCommissionRate").toString()))
				.isEqualByComparingTo("10.00");
	}

	@Test
	void completingAServiceAppointmentContributesServiceCommission() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateCommissions(headers, true);
		Setup setup = setUpProfessionalAndService(headers, "40.0", null);

		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-a");
		completeAppointment(headers, (String) booked.get("id"));

		Map row = findByProfessionalId(getCommissions(headers, null, null), setup.professionalId());
		assertThat(new BigDecimal(row.get("serviceRevenue").toString())).isEqualByComparingTo("50.00");
		assertThat(new BigDecimal(row.get("serviceCommission").toString())).isEqualByComparingTo("20.00");
	}

	@Test
	void aWalkInSaleWithAProfessionalContributesProductCommissionAndAnUnattributedSaleDoesNot() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateCommissions(headers, true);
		Setup setup = setUpProfessionalAndService(headers, null, "20.0");
		String productId = (String) post("/api/products", Map.of("name", "Wax", "price", 100.0, "stock", 10),
				headers).get("id");

		restTemplate.exchange("/api/sales", HttpMethod.POST,
				new HttpEntity<>(Map.of("productId", productId, "quantity", 1, "professionalId",
						setup.professionalId()), headers),
				Map.class);
		// Unattributed walk-in sale — no professionalId at all.
		restTemplate.exchange("/api/sales", HttpMethod.POST,
				new HttpEntity<>(Map.of("productId", productId, "quantity", 1), headers), Map.class);

		List<Map> rows = getCommissions(headers, null, null);
		Map row = findByProfessionalId(rows, setup.professionalId());
		assertThat(new BigDecimal(row.get("productRevenue").toString())).isEqualByComparingTo("100.00");
		assertThat(new BigDecimal(row.get("productCommission").toString())).isEqualByComparingTo("20.00");
	}

	@Test
	void aSaleTiedToAnAppointmentDerivesTheProfessionalIgnoringAConflictingRequestField() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateCommissions(headers, true);
		Setup setup = setUpProfessionalAndService(headers, null, "50.0");
		String branchId = (String) post("/api/branches", Map.of("name", "Second"), headers).get("id");
		String decoyProId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Decoy"), headers).get("id");
		restTemplate.exchange("/api/professionals/" + decoyProId, HttpMethod.PUT,
				new HttpEntity<>(
						Map.of("branchId", branchId, "displayName", "Decoy", "active", true, "productCommissionRate", 50.0),
						headers),
				Map.class);
		String productId = (String) post("/api/products", Map.of("name", "Gel", "price", 40.0, "stock", 10),
				headers).get("id");

		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-b");
		String appointmentId = (String) booked.get("id");

		// professionalId in the body points at the decoy, but the sale is tied to setup's appointment.
		restTemplate.exchange("/api/sales", HttpMethod.POST,
				new HttpEntity<>(Map.of("productId", productId, "quantity", 1, "appointmentId", appointmentId,
						"professionalId", decoyProId), headers),
				Map.class);

		List<Map> rows = getCommissions(headers, null, null);
		Map ownerRow = findByProfessionalId(rows, setup.professionalId());
		assertThat(new BigDecimal(ownerRow.get("productCommission").toString())).isEqualByComparingTo("20.00");
		// The decoy still shows up (they have a rate configured), but with nothing attributed to
		// them — the sale's actual professional came from the appointment, not the request body.
		Map decoyRow = findByProfessionalId(rows, decoyProId);
		assertThat(new BigDecimal(decoyRow.get("productCommission").toString())).isEqualByComparingTo("0");
	}

	@Test
	void reportRespectsTheDateRange() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateCommissions(headers, true);
		Setup setup = setUpProfessionalAndService(headers, "40.0", null);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-c");
		completeAppointment(headers, (String) booked.get("id"));

		String future = nextMonday().plusWeeks(10).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
		List<Map> outsideRange = getCommissions(headers, future, null);
		// Still shows up (the professional has a rate configured) but with nothing earned in a
		// range that excludes their only completed appointment.
		Map row = findByProfessionalId(outsideRange, setup.professionalId());
		assertThat(new BigDecimal(row.get("serviceCommission").toString())).isEqualByComparingTo("0");
	}

	@Test
	void reportIsEmptyWhileDisabledEvenWithRatesConfigured() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateCommissions(headers, true);
		Setup setup = setUpProfessionalAndService(headers, "40.0", null);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-d");
		completeAppointment(headers, (String) booked.get("id"));

		updateCommissions(headers, false);

		List<Map> rows = getCommissions(headers, null, null);
		assertThat(rows).isEmpty();
	}

	private void setPlanTier(RegisteredTenant tenant, String planTier) {
		jdbcTemplate.update("UPDATE tenants SET plan_tier = ? WHERE slug = ?", planTier, tenant.slug());
	}

	private ResponseEntity<Map> updateCommissions(HttpHeaders headers, boolean enabled) {
		return restTemplate.exchange("/api/tenant/commissions", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("enabled", enabled), headers), Map.class);
	}

	@SuppressWarnings("unchecked")
	private List<Map> getCommissions(HttpHeaders headers, String from, String to) {
		String query = (from != null ? "?from=" + from : "") + (to != null ? (from != null ? "&" : "?") + "to=" + to
				: "");
		ResponseEntity<Map[]> response = restTemplate.exchange("/api/reports/commissions" + query, HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return List.of(response.getBody());
	}

	private Map findByProfessionalId(List<Map> rows, String professionalId) {
		Map found = findByProfessionalIdOrNull(rows, professionalId);
		if (found == null) throw new AssertionError("No commission row found for professional " + professionalId);
		return found;
	}

	private Map findByProfessionalIdOrNull(List<Map> rows, String professionalId) {
		for (Map row : rows) {
			if (professionalId.equals(row.get("professionalId"))) return row;
		}
		return null;
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers, String serviceCommissionRate,
			String productCommissionRate) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		Map<String, Object> proBody = new java.util.HashMap<>();
		proBody.put("branchId", branchId);
		proBody.put("displayName", "Pro");
		proBody.put("serviceCommissionRate", serviceCommissionRate);
		proBody.put("productCommissionRate", productCommissionRate);
		String professionalId = (String) post("/api/professionals", proBody, headers).get("id");
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

	private void completeAppointment(HttpHeaders headers, String appointmentId) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/status",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "COMPLETED"), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
