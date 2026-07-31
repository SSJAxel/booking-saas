package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlanCatalogFlowTest extends IntegrationTestBase {

	@Test
	void listsBothTiersWithNoAuthenticationRequired() {
		ResponseEntity<List> response = restTemplate.getForEntity("/api/plans", List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> plans = response.getBody();
		assertThat(plans).extracting(p -> p.get("tier")).containsExactlyInAnyOrder("BASIC", "PRO");

		Map<String, Object> basic = plans.stream().filter(p -> "BASIC".equals(p.get("tier"))).findFirst().orElseThrow();
		assertThat(((Number) basic.get("monthlyPrice")).doubleValue()).isZero();
		assertThat(basic.get("maxProducts")).isEqualTo(5);

		Map<String, Object> pro = plans.stream().filter(p -> "PRO".equals(p.get("tier"))).findFirst().orElseThrow();
		assertThat(((Number) pro.get("monthlyPrice")).doubleValue()).isGreaterThan(0);
		assertThat(pro.get("maxProducts")).isNull();
	}
}
