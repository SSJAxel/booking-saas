package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code mercadoPagoEnabled} on {@code GET /api/public/{tenantSlug}} — the one plan-derived bit the
 * public booking page needs to decide whether to offer a "Pagar con Mercado Pago" button for a
 * pending deposit instead of only ever showing the transfer-alias fallback (real gap found
 * 2026-08-19: lusitattoo had connected their own Mercado Pago account, but the public booking flow
 * never actually called the already-built/verified checkout endpoint, so clients only ever saw the
 * alias instructions).
 */
class PublicTenantResponseFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void mercadoPagoEnabledReflectsThePlanTier() {
		RegisteredTenant tenant = registerTenant(); // defaults to TRIAL, no Mercado Pago

		ResponseEntity<Map> trialResponse = restTemplate.getForEntity("/api/public/" + tenant.slug(), Map.class);
		assertThat(trialResponse.getBody().get("mercadoPagoEnabled")).isEqualTo(false);

		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());

		ResponseEntity<Map> proResponse = restTemplate.getForEntity("/api/public/" + tenant.slug(), Map.class);
		assertThat(proResponse.getBody().get("mercadoPagoEnabled")).isEqualTo(true);
	}
}
