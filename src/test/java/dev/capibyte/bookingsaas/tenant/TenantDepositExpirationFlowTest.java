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

class TenantDepositExpirationFlowTest extends IntegrationTestBase {

	@Test
	void freshTenantDefaultsToThirtyMinutes() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);

		assertThat(response.getBody().get("depositExpirationMinutes")).isEqualTo(30);
	}

	@Test
	void updatingDepositExpirationWithinBoundsIsReflected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/deposit-expiration", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("depositExpirationMinutes", 60), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().get("depositExpirationMinutes")).isEqualTo(60);
	}

	@Test
	void rejectsBelowTheMinimum() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/deposit-expiration", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("depositExpirationMinutes", 9), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsAboveTheMaximum() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/deposit-expiration", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("depositExpirationMinutes", 181), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
