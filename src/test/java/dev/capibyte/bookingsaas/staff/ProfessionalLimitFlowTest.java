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
	void trialPlanRejectsTheFifthProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default — no need to set the tier here.
		String branchId = createBranch(headers);

		for (int i = 0; i < 4; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> fifth = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 4"), headers), Map.class);
		assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void basicPlanRejectsTheFifthProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'BASIC' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 4; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> fifth = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 4"), headers), Map.class);
		assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void proPlanRejectsTheEleventhProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 10; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> eleventh = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 10"), headers), Map.class);
		assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void maxPlanRejectsThe21stProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());
		String branchId = createBranch(headers);

		for (int i = 0; i < 20; i++) {
			createProfessional(headers, branchId, "Pro " + i);
		}

		ResponseEntity<Map> twentyFirst = restTemplate.exchange("/api/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro 20"), headers), Map.class);
		assertThat(twentyFirst.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
