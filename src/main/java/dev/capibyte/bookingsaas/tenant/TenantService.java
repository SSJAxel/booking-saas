package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.DateTimeException;
import java.time.ZoneId;
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

	private final TenantRepository tenantRepository;

	@Transactional
	public Tenant create(String name, String slug) {
		if (tenantRepository.existsBySlug(slug)) {
			throw new TenantSlugTakenException(slug);
		}
		Tenant tenant = new Tenant();
		tenant.setName(name);
		tenant.setSlug(slug);
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
	public Tenant updateBranding(UUID tenantId, String logoUrl, String accentColor, String tagline) {
		Tenant tenant = findById(tenantId);
		tenant.setLogoUrl(logoUrl);
		tenant.setAccentColor(accentColor);
		tenant.setTagline(tagline);
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
}
