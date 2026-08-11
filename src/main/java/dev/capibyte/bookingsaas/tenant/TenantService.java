package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as its own bean (rather than folded into AuthService) so its @Transactional methods
 * open their own Hibernate session at a clean boundary. Tenant isn't @TenantId-scoped, so this
 * works with or without a tenant in {@link dev.capibyte.bookingsaas.common.TenantContext} —
 * critical for register()/login(), which don't know the tenant yet when they start.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

	private static final int TRIAL_DAYS = 15;

	private final TenantRepository tenantRepository;

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

	@Transactional
	public void delete(UUID tenantId) {
		tenantRepository.deleteById(tenantId);
	}

	/** Any field left null clears that piece of branding — an unbranded tenant is a valid state. */
	@Transactional
	public Tenant updateBranding(UUID tenantId, String logoUrl, String accentColor, String tagline,
			String contactEmail, String whatsappNumber, String transferAlias) {
		Tenant tenant = findById(tenantId);
		tenant.setLogoUrl(logoUrl);
		tenant.setAccentColor(accentColor);
		tenant.setTagline(tagline);
		tenant.setContactEmail(contactEmail);
		tenant.setWhatsappNumber(whatsappNumber);
		tenant.setTransferAlias(transferAlias);
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
		tenant.setWhatsappEnabled(enabled);
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
}
