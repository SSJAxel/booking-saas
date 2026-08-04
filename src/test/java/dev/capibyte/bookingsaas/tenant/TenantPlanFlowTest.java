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

/**
 * The full subscribe → MercadoPago webhook → PRO flow is covered at the unit level
 * (SubscriptionServiceTest) instead of here, since exercising it over real HTTP would mean this
 * integration test actually calling out to MercadoPago — not viable without live credentials
 * (see README). What's safe to verify end to end is the guard that keeps a paid tier from being
 * set for free.
 */
class TenantPlanFlowTest extends IntegrationTestBase {

	@Test
	void newTenantStartsOnTrialPlan() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(response.getBody().get("planTier")).isEqualTo("TRIAL");
	}

	@Test
	void patchingStraightToAPaidTierIsRejected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "PRO"), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void patchingToTheFreeTierIsAllowedAndIsANoOpWhenAlreadyThere() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> firstPatch = restTemplate.exchange("/api/tenant/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "BASIC"), headers), Map.class);
		assertThat(firstPatch.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(firstPatch.getBody().get("planTier")).isEqualTo("BASIC");

		ResponseEntity<Map> secondPatch = restTemplate.exchange("/api/tenant/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "BASIC"), headers), Map.class);
		assertThat(secondPatch.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(secondPatch.getBody().get("planTier")).isEqualTo("BASIC");
	}
}
