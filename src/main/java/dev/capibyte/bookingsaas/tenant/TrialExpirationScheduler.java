package dev.capibyte.bookingsaas.tenant;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-downgrades a TRIAL ("Demo" in the UI) tenant to PERSONAL once
 * {@link Tenant#getTrialExpiresAt} has passed — the tenant keeps working (PERSONAL is the one paid
 * tier a tenant lands on without an active subscription, same fallback SubscriptionService uses on
 * cancellation), nothing is blocked. Only ever touches tenants with a non-null, past
 * {@code trialExpiresAt} — see that field's Javadoc for why every tenant that existed before this
 * feature shipped is naturally exempt. Doesn't need TenantContext/per-tenant looping like
 * PendingDepositExpirationScheduler: {@link Tenant} isn't {@code @TenantId}-scoped (see its own
 * Javadoc), so a normal repository query already reads across every tenant in one go.
 */
@Component
public class TrialExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(TrialExpirationScheduler.class);

	private final TenantRepository tenantRepository;

	public TrialExpirationScheduler(TenantRepository tenantRepository) {
		this.tenantRepository = tenantRepository;
	}

	@Scheduled(fixedDelayString = "${app.trial.expiration-check-interval-ms:3600000}")
	@Transactional
	public void expireTrials() {
		List<Tenant> expired = tenantRepository.findByPlanTierAndTrialExpiresAtBefore(PlanTier.TRIAL, Instant.now());
		for (Tenant tenant : expired) {
			log.info("Demo expired for tenant {} ({}): downgrading TRIAL -> PERSONAL", tenant.getId(), tenant.getSlug());
			tenant.setPlanTier(PlanTier.PERSONAL);
		}
	}
}
