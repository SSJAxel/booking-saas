package dev.capibyte.bookingsaas.tenant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Backs TrialExpirationScheduler — {@code trialExpiresAt} is null for every tenant that
	 * existed before that feature shipped, so this naturally never matches them. */
	List<Tenant> findByPlanTierAndTrialExpiresAtBefore(PlanTier planTier, Instant cutoff);
}
