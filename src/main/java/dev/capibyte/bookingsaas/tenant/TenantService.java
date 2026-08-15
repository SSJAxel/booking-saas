package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as its own bean (rather than folded into AuthService) so its @Transactional methods
 * open their own Hibernate session at a clean boundary. Tenant isn't @TenantId-scoped, so this
 * works with or without a tenant in {@link dev.capibyte.bookingsaas.common.TenantContext} —
 * critical for register()/login(), which don't know the tenant yet when they start.
 */
@Service
public class TenantService {

	private static final Logger log = LoggerFactory.getLogger(TenantService.class);

	private static final int TRIAL_DAYS = 15;

	private final TenantRepository tenantRepository;
	private final RewardTierRepository rewardTierRepository;

	/** Seeds Tenant.depositExpirationMinutes for every newly-registered tenant — the org-wide
	 * default, overridable per tenant afterward via updateDepositExpirationMinutes. Written as an
	 * explicit constructor (not @RequiredArgsConstructor) because Lombok doesn't reliably copy
	 * Spring's @Value onto a generated constructor parameter — it collides with Lombok's own,
	 * unrelated @Value annotation of the same simple name. */
	private final int defaultDepositExpirationMinutes;

	public TenantService(TenantRepository tenantRepository, RewardTierRepository rewardTierRepository,
			@Value("${app.booking.deposit-expiration-minutes:30}") int defaultDepositExpirationMinutes) {
		this.tenantRepository = tenantRepository;
		this.rewardTierRepository = rewardTierRepository;
		this.defaultDepositExpirationMinutes = defaultDepositExpirationMinutes;
	}

	@Transactional
	public Tenant create(String name, String slug) {
		if (tenantRepository.existsBySlug(slug)) {
			throw new TenantSlugTakenException(slug);
		}
		Tenant tenant = new Tenant();
		tenant.setName(name);
		tenant.setSlug(slug);
		// Every new tenant starts on TRIAL ("Demo" in the UI — see V13); this is what
		// TrialExpirationScheduler reads to auto-downgrade to BASIC after TRIAL_DAYS. Left null by
		// every migration before this feature, so it never touches a tenant that already existed.
		tenant.setTrialExpiresAt(Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS));
		// Founder reviews every new business (and how many professionals it set up — pricing is
		// per-employee) before its public booking site goes live. See TenantStatus's Javadoc and
		// PublicTenantResolutionFilter. Every tenant that existed before this feature shipped is
		// already ACTIVE in the DB, so this default only ever applies going forward.
		tenant.setStatus(TenantStatus.PENDING_APPROVAL);
		tenant.setDepositExpirationMinutes(defaultDepositExpirationMinutes);
		return tenantRepository.save(tenant);
	}

	@Transactional(readOnly = true)
	public Optional<Tenant> findBySlug(String slug) {
		return tenantRepository.findBySlug(slug);
	}

	@Transactional(readOnly = true)
	public Tenant findById(UUID id) {
		return tenantRepository.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found: " + id));
	}

	/** Same as {@link #findById} but takes a pessimistic write lock on the tenant row, held for the
	 * rest of the caller's transaction — for the handful of check-then-insert call sites (plan
	 * limits on professionals/branches/services/weekly appointments) where two concurrent requests
	 * could otherwise both read a stale count and both pass. Deliberately a separate method, not a
	 * flag on findById: most callers just read the tenant and must not pay for a write lock. */
	@Transactional
	public Tenant findByIdForUpdate(UUID id) {
		return tenantRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Tenant not found: " + id));
	}

	/**
	 * Only free tiers can be set directly here — a paid one requires an authorized subscription
	 * (see SubscriptionService.subscribe), which flips planTier itself once MercadoPago confirms
	 * it via webhook. This is the downgrade/cancellation path, not the upgrade path. Downgrading
	 * below the new tier's product limit isn't blocked here: existing products over the limit stay
	 * active (grandfathered), ProductService.create() just refuses new ones until the tenant is
	 * back under it.
	 */
	@Transactional
	public Tenant changePlan(UUID tenantId, PlanTier newTier) {
		if (!newTier.isFree()) {
			throw new BadRequestException(
					"Paid plans require an active subscription — use POST /api/tenant/subscription instead");
		}
		Tenant tenant = findById(tenantId);
		tenant.setPlanTier(newTier);
		return tenant;
	}

	/**
	 * The upgrade/downgrade path driven by a MercadoPago subscription webhook (see
	 * SubscriptionService.handleWebhook) — unlike changePlan, allowed to set a paid tier, since the
	 * whole point is reacting to a just-confirmed subscription. Needs its own @Transactional
	 * boundary: findById alone returns a detached entity once its own read-only transaction
	 * closes, so a bare setPlanTier() on that result from a non-transactional caller would be lost
	 * — this method's transaction covers the fetch and the mutation together so the dirty-checked
	 * update actually flushes.
	 */
	@Transactional
	public void applyPlanTierFromSubscription(UUID tenantId, PlanTier tier) {
		Tenant tenant = findById(tenantId);
		if (tenant.isPlanManuallySet()) {
			log.warn("Ignored a webhook trying to set tenant {} to {} — plan was manually set by the founder "
					+ "(currently {}). It only resumes reacting to webhooks after a fresh SubscriptionService.subscribe checkout.",
					tenantId, tier, tenant.getPlanTier());
			return;
		}
		tenant.setPlanTier(tier);
	}

	@Transactional
	public void delete(UUID tenantId) {
		tenantRepository.deleteById(tenantId);
	}

	/** Any field left null clears that piece of branding — an unbranded tenant is a valid state. */
	@Transactional
	public Tenant updateBranding(UUID tenantId, String logoUrl, String bannerUrl, String accentColor, String tagline,
			String contactEmail, String whatsappNumber, String transferAlias, String instagramUrl,
			String facebookUrl, String instagramFeedUrl) {
		Tenant tenant = findById(tenantId);
		tenant.setLogoUrl(logoUrl);
		tenant.setBannerUrl(bannerUrl);
		tenant.setAccentColor(accentColor);
		tenant.setTagline(tagline);
		tenant.setContactEmail(contactEmail);
		tenant.setWhatsappNumber(whatsappNumber);
		tenant.setTransferAlias(transferAlias);
		tenant.setInstagramUrl(instagramUrl);
		tenant.setFacebookUrl(facebookUrl);
		tenant.setInstagramFeedUrl(instagramFeedUrl);
		return tenant;
	}

	/** Narrower than updateBranding — used by the logo/banner upload endpoints, which only ever
	 * touch their own field and shouldn't require resending the rest of the branding form. */
	@Transactional
	public Tenant updateLogoUrl(UUID tenantId, String logoUrl) {
		Tenant tenant = findById(tenantId);
		tenant.setLogoUrl(logoUrl);
		return tenant;
	}

	@Transactional
	public Tenant updateBannerUrl(UUID tenantId, String bannerUrl) {
		Tenant tenant = findById(tenantId);
		tenant.setBannerUrl(bannerUrl);
		return tenant;
	}

	/**
	 * {@code timezone} must be a valid IANA zone id (e.g. "America/Argentina/Buenos_Aires") — this
	 * is what AppointmentService/PublicAvailabilityService convert every wall-clock time against,
	 * so an invalid value here would silently corrupt every availability search and booking for
	 * this tenant. Validated by attempting the parse rather than a regex, since "looks like a zone
	 * id" and "is a zone id ZoneId actually recognizes" aren't the same check.
	 */
	@Transactional
	public Tenant updateTimezone(UUID tenantId, String timezone) {
		try {
			ZoneId.of(timezone);
		} catch (DateTimeException e) {
			throw new BadRequestException("Unknown timezone: " + timezone);
		}
		Tenant tenant = findById(tenantId);
		tenant.setTimezone(timezone);
		return tenant;
	}

	@Transactional(readOnly = true)
	public ZoneId getZoneId(UUID tenantId) {
		return ZoneId.of(findById(tenantId).getTimezone());
	}

	@Transactional
	public Tenant updateWhatsAppEnabled(UUID tenantId, boolean enabled) {
		Tenant tenant = findById(tenantId);
		if (enabled && !tenant.getPlanTier().isWhatsappEnabled()) {
			throw new BadRequestException("Plan " + tenant.getPlanTier() + " doesn't include WhatsApp notifications");
		}
		tenant.setWhatsappEnabled(enabled);
		return tenant;
	}

	@Transactional
	public Tenant updateCommissionsEnabled(UUID tenantId, boolean enabled) {
		Tenant tenant = findById(tenantId);
		if (enabled && !tenant.getPlanTier().isCommissionsEnabled()) {
			throw new BadRequestException("Plan " + tenant.getPlanTier() + " doesn't include staff commissions");
		}
		tenant.setCommissionsEnabled(enabled);
		return tenant;
	}

	@Transactional
	public Tenant updateReviewsEnabled(UUID tenantId, boolean enabled) {
		Tenant tenant = findById(tenantId);
		if (enabled && !tenant.getPlanTier().isReviewsEnabled()) {
			throw new BadRequestException("Plan " + tenant.getPlanTier() + " doesn't include public reviews");
		}
		tenant.setReviewsEnabled(enabled);
		return tenant;
	}

	/** {@code pointsCap} bounds (5–200) are also enforced by validation on the request DTO and a DB
	 * check constraint (V39) — same three-places-deliberately pattern as updateClientRankingSettings.
	 * Also refuses to drop the cap below an existing RewardTier's requirement — a tier a client can
	 * never reach is a silent trap, not a valid configuration. */
	@Transactional
	public Tenant updateLoyaltyRewardsSettings(UUID tenantId, boolean enabled, int pointsCap) {
		Tenant tenant = findById(tenantId);
		if (enabled && !tenant.getPlanTier().isLoyaltyRewardsEnabled()) {
			throw new BadRequestException("Plan " + tenant.getPlanTier() + " doesn't include loyalty rewards");
		}
		if (pointsCap < 5 || pointsCap > 200) {
			throw new BadRequestException("loyaltyPointsCap must be between 5 and 200");
		}
		if (rewardTierRepository.existsByPointsRequiredGreaterThan(pointsCap)) {
			throw new BadRequestException(
					"loyaltyPointsCap can't be lower than an existing reward tier's points requirement");
		}
		tenant.setLoyaltyRewardsEnabled(enabled);
		tenant.setLoyaltyPointsCap(pointsCap);
		return tenant;
	}

	/** {@code topClientsCount} bounds (1–15) are also enforced by validation on the request DTO and
	 * a DB check constraint (V21) — kept in three places deliberately: the DTO gives a clean 400 for
	 * the API, this method is the one source of truth if this is ever called from somewhere that
	 * skips DTO validation, and the DB constraint is the last line of defense against any code path
	 * that bypasses both. */
	@Transactional
	public Tenant updateClientRankingSettings(UUID tenantId, int topClientsThreshold, int topClientsCount) {
		if (topClientsCount < 1 || topClientsCount > 15) {
			throw new BadRequestException("topClientsCount must be between 1 and 15");
		}
		Tenant tenant = findById(tenantId);
		tenant.setTopClientsThreshold(topClientsThreshold);
		tenant.setTopClientsCount(topClientsCount);
		return tenant;
	}

	/** {@code months} bounds (1–12) are also enforced by validation on the request DTO and a DB
	 * check constraint (V27) — same three-places-deliberately pattern as updateClientRankingSettings. */
	@Transactional
	public Tenant updateHistoryRetentionMonths(UUID tenantId, int months) {
		if (months < 1 || months > 12) {
			throw new BadRequestException("historyRetentionMonths must be between 1 and 12");
		}
		Tenant tenant = findById(tenantId);
		tenant.setHistoryRetentionMonths(months);
		return tenant;
	}

	/** {@code minutes} bounds (10–180) are also enforced by validation on the request DTO and a DB
	 * check constraint (V32) — same three-places-deliberately pattern as updateClientRankingSettings.
	 * Only has an effect for tenants with MercadoPago enabled — see
	 * PendingDepositExpirationScheduler's Javadoc. */
	@Transactional
	public Tenant updateDepositExpirationMinutes(UUID tenantId, int minutes) {
		if (minutes < 10 || minutes > 180) {
			throw new BadRequestException("depositExpirationMinutes must be between 10 and 180");
		}
		Tenant tenant = findById(tenantId);
		tenant.setDepositExpirationMinutes(minutes);
		return tenant;
	}
}
