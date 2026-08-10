package dev.capibyte.bookingsaas.tenant;

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

class BranchLimitFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void trialPlanRejectsTheThirdBranch() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default — no need to set the tier here, unlike the other cases
		// below which care about a specific paid tier.

		createBranch(headers, "Branch 0");
		createBranch(headers, "Branch 1");

		ResponseEntity<Map> third = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Branch 2"), headers), Map.class);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void basicPlanRejectsTheSecondBranch() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'BASIC' WHERE slug = ?", tenant.slug());

		createBranch(headers, "Branch 0");

		ResponseEntity<Map> second = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Branch 1"), headers), Map.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void proPlanRejectsTheThirdBranch() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());

		createBranch(headers, "Branch 0");
		createBranch(headers, "Branch 1");

		ResponseEntity<Map> third = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Branch 2"), headers), Map.class);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void maxPlanRejectsTheFifthBranch() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());

		for (int i = 0; i < 4; i++) {
			createBranch(headers, "Branch " + i);
		}

		ResponseEntity<Map> fifth = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Branch 4"), headers), Map.class);
		assertThat(fifth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private void createBranch(HttpHeaders headers, String name) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", name), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}
}
