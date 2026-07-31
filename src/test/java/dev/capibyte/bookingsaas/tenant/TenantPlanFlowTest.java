package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantPlanFlowTest extends IntegrationTestBase {

	@Test
	void newTenantStartsOnBasicPlanAndCanUpgradeToPro() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> initial = restTemplate.exchange("/api/tenant", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(initial.getBody().get("planTier")).isEqualTo("BASIC");

		ResponseEntity<Map> afterUpgrade = restTemplate.exchange("/api/tenant/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "PRO"), headers), Map.class);
		assertThat(afterUpgrade.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(afterUpgrade.getBody().get("planTier")).isEqualTo("PRO");

		ResponseEntity<Map> afterReload = restTemplate.exchange("/api/tenant", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(afterReload.getBody().get("planTier")).isEqualTo("PRO");
	}
}
