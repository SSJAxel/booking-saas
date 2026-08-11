package dev.capibyte.bookingsaas.catalog;

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

class ServiceLimitFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void personalPlanRejectsTheFourthService() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());

		for (int i = 0; i < 3; i++) {
			createService(headers, "Service " + i);
		}

		ResponseEntity<Map> fourth = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody("Service 3"), headers), Map.class);
		assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void trialPlanRejectsTheSeventhService() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default — no need to set the tier here.

		for (int i = 0; i < 6; i++) {
			createService(headers, "Service " + i);
		}

		ResponseEntity<Map> seventh = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody("Service 6"), headers), Map.class);
		assertThat(seventh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void basicPlanRejectsTheSeventhService() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'BASIC' WHERE slug = ?", tenant.slug());

		for (int i = 0; i < 6; i++) {
			createService(headers, "Service " + i);
		}

		ResponseEntity<Map> seventh = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody("Service 6"), headers), Map.class);
		assertThat(seventh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void proPlanRejectsTheNinthService() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());

		for (int i = 0; i < 8; i++) {
			createService(headers, "Service " + i);
		}

		ResponseEntity<Map> ninth = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody("Service 8"), headers), Map.class);
		assertThat(ninth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void maxPlanRejectsThe13thService() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'MAX' WHERE slug = ?", tenant.slug());

		for (int i = 0; i < 12; i++) {
			createService(headers, "Service " + i);
		}

		ResponseEntity<Map> thirteenth = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody("Service 12"), headers), Map.class);
		assertThat(thirteenth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private void createService(HttpHeaders headers, String name) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/services", HttpMethod.POST,
				new HttpEntity<>(serviceBody(name), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private Map<String, Object> serviceBody(String name) {
		return Map.of("name", name, "durationMinutes", 30, "price", 50.0);
	}
}
