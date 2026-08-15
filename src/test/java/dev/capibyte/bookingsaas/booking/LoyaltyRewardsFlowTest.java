package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The loyalty points system: 1 point per COMPLETED appointment (only while enabled, PRO/MAX only),
 * clamped at a tenant-configurable cap, spent (not reset) against tenant-defined reward tiers on
 * redemption. Books through /api/appointments/overtime, same trick
 * WeeklyAppointmentLimitFlowTest uses, so the same professional/slot can be reused for every
 * completed visit without fighting the no_double_booking constraint.
 */
class LoyaltyRewardsFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void enablingRewardsIsRejectedOnPlansWithoutTheFeatureAndAllowedOnProAndMax() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// TRIAL by default.
		ResponseEntity<Map> onTrial = updateLoyaltySettings(headers, true, 25);
		assertThat(onTrial.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		setPlanTier(tenant, "PRO");
		ResponseEntity<Map> onPro = updateLoyaltySettings(headers, true, 25);
		assertThat(onPro.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(onPro.getBody().get("loyaltyRewardsEnabled")).isEqualTo(true);
	}

	@Test
	void completingAppointmentsEarnsAPointEachClampedAtTheCap() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		ResponseEntity<Map> settingsResponse = updateLoyaltySettings(headers, true, 5);
		assertThat(settingsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Setup setup = setUpProfessionalAndService(headers);
		LocalDateTime slot = nextMonday().atTime(10, 0);

		String clientId = null;
		for (int i = 0; i < 8; i++) {
			Map<String, Object> booked = createOvertime(headers, setup, slot, "repeat-client");
			clientId = (String) booked.get("clientId");
			completeAppointment(headers, (String) booked.get("id"));
		}

		Map clientStats = findClientStats(headers, clientId);
		assertThat(clientStats.get("loyaltyPoints")).isEqualTo(5);
	}

	@Test
	void pointsDoNotMoveWhileRewardsAreDisabled() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		// Never enabled.
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-x");
		completeAppointment(headers, (String) booked.get("id"));

		Map clientStats = findClientStats(headers, (String) booked.get("clientId"));
		assertThat(clientStats.get("loyaltyPoints")).isEqualTo(0);
	}

	@Test
	void tierCreationEnforcesTheFiveTierCapAndTheCapBound() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateLoyaltySettings(headers, true, 10);

		for (int i = 1; i <= 5; i++) {
			ResponseEntity<Map> created = createTier(headers, i, "Reward " + i);
			assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}
		ResponseEntity<Map> sixth = createTier(headers, 6, "One too many");
		assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> aboveCap = createTier(headers, 20, "Unreachable");
		assertThat(aboveCap.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void loweringTheCapBelowAnExistingTierIsRejected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateLoyaltySettings(headers, true, 25);
		createTier(headers, 20, "20 point reward");

		ResponseEntity<Map> loweredTooFar = updateLoyaltySettings(headers, true, 10);
		assertThat(loweredTooFar.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void redeemingSpendsPointsInsteadOfResettingAndRejectsAnInsufficientBalance() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateLoyaltySettings(headers, true, 25);
		String tierId = (String) createTier(headers, 10, "10 point reward").getBody().get("id");
		Setup setup = setUpProfessionalAndService(headers);
		LocalDateTime slot = nextMonday().atTime(10, 0);

		String clientId = null;
		for (int i = 0; i < 12; i++) {
			Map<String, Object> booked = createOvertime(headers, setup, slot, "repeat-client");
			clientId = (String) booked.get("clientId");
			completeAppointment(headers, (String) booked.get("id"));
		}
		Map afterEarning = findClientStats(headers, clientId);
		assertThat(afterEarning.get("loyaltyPoints")).isEqualTo(12);

		ResponseEntity<Map> redeemed = restTemplate.exchange("/api/clients/" + clientId + "/redeem-reward",
				HttpMethod.POST, new HttpEntity<>(Map.of("rewardTierId", tierId), headers), Map.class);
		assertThat(redeemed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(redeemed.getBody().get("loyaltyPoints")).isEqualTo(2);

		ResponseEntity<Map> tooFewPoints = restTemplate.exchange("/api/clients/" + clientId + "/redeem-reward",
				HttpMethod.POST, new HttpEntity<>(Map.of("rewardTierId", tierId), headers), Map.class);
		assertThat(tooFewPoints.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void staffCanRedeemButCannotCreateTiers() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateLoyaltySettings(headers, true, 25);
		String tierId = (String) createTier(headers, 1, "1 point reward").getBody().get("id");
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-staff");
		completeAppointment(headers, (String) booked.get("id"));
		String clientId = (String) booked.get("clientId");

		HttpHeaders staffHeaders = createStaffAndLogin(tenant);

		ResponseEntity<Map> staffCreate = createTier(staffHeaders, 2, "Should be forbidden");
		assertThat(staffCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<Map> staffRedeem = restTemplate.exchange("/api/clients/" + clientId + "/redeem-reward",
				HttpMethod.POST, new HttpEntity<>(Map.of("rewardTierId", tierId), staffHeaders), Map.class);
		assertThat(staffRedeem.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private void setPlanTier(RegisteredTenant tenant, String planTier) {
		jdbcTemplate.update("UPDATE tenants SET plan_tier = ? WHERE slug = ?", planTier, tenant.slug());
	}

	private ResponseEntity<Map> updateLoyaltySettings(HttpHeaders headers, boolean enabled, int pointsCap) {
		return restTemplate.exchange("/api/tenant/loyalty-rewards", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("enabled", enabled, "pointsCap", pointsCap), headers), Map.class);
	}

	private ResponseEntity<Map> createTier(HttpHeaders headers, int pointsRequired, String description) {
		return restTemplate.exchange("/api/tenant/loyalty-rewards/tiers", HttpMethod.POST,
				new HttpEntity<>(Map.of("pointsRequired", pointsRequired, "description", description), headers),
				Map.class);
	}

	private void completeAppointment(HttpHeaders headers, String appointmentId) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/status",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "COMPLETED"), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private Map findClientStats(HttpHeaders headers, String clientId) {
		ResponseEntity<Map[]> stats = restTemplate.exchange("/api/reports/clients", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		for (Map row : stats.getBody()) {
			if (clientId.equals(row.get("clientId"))) return row;
		}
		throw new AssertionError("No client stats found for " + clientId);
	}

	private HttpHeaders createStaffAndLogin(RegisteredTenant tenant) {
		String staffEmail = "staff-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update(
				"INSERT INTO app_users (id, tenant_id, email, password_hash, role, active, email_verified) "
						+ "SELECT gen_random_uuid(), tenant_id, ?, password_hash, 'STAFF', true, true FROM app_users WHERE email = ?",
				staffEmail, tenant.slug() + "@example.com");
		Map<String, Object> loginRequest = Map.of("tenantSlug", tenant.slug(), "email", staffEmail, "password",
				"supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		return authHeaders((String) loginResponse.getBody().get("token"));
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
