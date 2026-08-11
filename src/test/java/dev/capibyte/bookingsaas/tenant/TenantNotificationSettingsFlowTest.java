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

class TenantNotificationSettingsFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void personalPlanRejectsTurningWhatsappOnButStillAllowsTurningItOff() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());

		ResponseEntity<Map> turnOn = restTemplate.exchange("/api/tenant/notifications", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("whatsappEnabled", true), headers), Map.class);
		assertThat(turnOn.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> turnOff = restTemplate.exchange("/api/tenant/notifications", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("whatsappEnabled", false), headers), Map.class);
		assertThat(turnOff.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void requiresAuthentication() {
		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/notifications", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("whatsappEnabled", true)), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
