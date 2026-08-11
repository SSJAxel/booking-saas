package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * TRIAL ("Demo" in the UI) auto-downgrades to PERSONAL once TenantService.create's 15-day
 * trialExpiresAt has passed — see TrialExpirationScheduler's Javadoc for why a tenant that
 * predates this feature (trialExpiresAt null) is never touched.
 */
class TrialExpirationFlowTest extends IntegrationTestBase {

	@Autowired
	private TrialExpirationScheduler trialExpirationScheduler;

	@Test
	void trialPastItsExpirationDateIsDowngradedToPersonal() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		assertThat(getPlanTier(headers)).isEqualTo("TRIAL");

		Instant longAgo = Instant.now().minus(1, ChronoUnit.DAYS);
		jdbcTemplate.update("UPDATE tenants SET trial_expires_at = ? WHERE slug = ?", Timestamp.from(longAgo),
				tenant.slug());

		trialExpirationScheduler.expireTrials();

		assertThat(getPlanTier(headers)).isEqualTo("PERSONAL");
	}

	@Test
	void trialNotYetExpiredIsUntouched() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		trialExpirationScheduler.expireTrials();

		assertThat(getPlanTier(headers)).isEqualTo("TRIAL");
	}

	@Test
	void aTenantWithNoExpirationDateIsNeverTouchedEvenIfVeryOld() {
		// Simulates a tenant that existed before this feature shipped: trial_expires_at stays
		// null forever for it (see TrialExpirationScheduler's Javadoc), no matter how old it is.
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET trial_expires_at = NULL WHERE slug = ?", tenant.slug());

		trialExpirationScheduler.expireTrials();

		assertThat(getPlanTier(headers)).isEqualTo("TRIAL");
	}

	private String getPlanTier(HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant", HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		return (String) response.getBody().get("planTier");
	}
}
