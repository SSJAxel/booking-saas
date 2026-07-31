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

class TenantNotificationSettingsFlowTest extends IntegrationTestBase {

	@Test
	void whatsappIsOffByDefaultAndCanBeToggledOn() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> initial = restTemplate.exchange("/api/tenant", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(initial.getBody().get("whatsappEnabled")).isEqualTo(false);

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/notifications", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("whatsappEnabled", true), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().get("whatsappEnabled")).isEqualTo(true);

		ResponseEntity<Map> reFetched = restTemplate.exchange("/api/tenant", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(reFetched.getBody().get("whatsappEnabled")).isEqualTo(true);
	}

	@Test
	void requiresAuthentication() {
		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/notifications", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("whatsappEnabled", true)), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
