package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.NotFoundException;
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
	 * Stands in for what should eventually be driven by a billing webhook, not a direct client
	 * call — there's no payment integration behind plan changes yet. Downgrading below the new
	 * tier's product limit isn't blocked here: existing products over the limit stay active
	 * (grandfathered), ProductService.create() just refuses new ones until the tenant is back
	 * under it.
	 */
	@Transactional
	public Tenant changePlan(UUID tenantId, PlanTier newTier) {
		Tenant tenant = findById(tenantId);
		tenant.setPlanTier(newTier);
		return tenant;
	}

	@Transactional
	public void delete(UUID tenantId) {
		tenantRepository.deleteById(tenantId);
	}
}
