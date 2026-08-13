package dev.capibyte.bookingsaas.staff;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Límites de empleados incluidos por defecto, matriz 2026-08-11 (ver PlanTier's Javadoc) — no son
 * un techo técnico duro, solo el valor por defecto hasta que el founder fija un
 * {@code professionalLimitOverride} a mano (ver
 * {@code professionalLimitOverrideRaisesTheCapBeyondThePlanDefault} más abajo). */
class ProfessionalLimitFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void personalPlanRejectsTheSecondProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		createProfessional(headers, branchId, "Pro 0");

		ResponseEntity<Map> second = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 1"), headers), Map.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void trialPlanRejectsTheThirdProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default — no need to set the tier here.
		String branchId = createBranch(headers);

		for (int i = 0; i < 2; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> third = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 2"), headers), Map.class);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void basicPlanRejectsTheThirdProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'BASIC' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 2; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> third = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 2"), headers), Map.class);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void proPlanRejectsTheSixthProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 5; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> sixth = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 5"), headers), Map.class);
		assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void maxPlanRejectsThe11thProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 10; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> eleventh = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 10"), headers), Map.class);
		assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/** The founder-set override (not self-service, see PlanTier's Javadoc) is how a tenant gets
	 * more employees within the same tier — e.g. BASIC (default 2) raised to 3 by hand. */
	@Test
	void professionalLimitOverrideRaisesTheCapBeyondThePlanDefault() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'BASIC' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);
		createProfessional(headers, branchId, "Pro 0");
		createProfessional(headers, branchId, "Pro 1");

		ResponseEntity<Map> blocked = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 2"), headers), Map.class);
		assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		jdbcTemplate.update("UPDATE app_users SET platform_admin = true WHERE email = ?", tenant.slug() + "@example.com");
		Map<String, Object> loginRequest = Map.of("tenantSlug", tenant.slug(), "email", tenant.slug() + "@example.com",
				"password", "supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		HttpHeaders adminHeaders = authHeaders((String) loginResponse.getBody().get("token"));
		ResponseEntity<Map[]> tenants = restTemplate.exchange("/api/admin/tenants", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		String tenantId = null;
		for (Map t : tenants.getBody()) {
			if (tenant.slug().equals(t.get("slug"))) {
				tenantId = (String) t.get("tenantId");
			}
		}
		restTemplate.exchange("/api/admin/tenants/" + tenantId + "/professional-limit", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("professionalLimitOverride", 3), adminHeaders), Map.class);

		ResponseEntity<Map> allowed = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 2"), headers), Map.class);
		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private String createBranch(HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Main"), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return (String) response.getBody().get("id");
	}

	private void createProfessional(HttpHeaders headers, String branchId, String displayName) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", displayName), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}
}
