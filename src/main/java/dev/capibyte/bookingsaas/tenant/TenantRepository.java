package dev.capibyte.bookingsaas.tenant;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Backs TenantService#findByIdForUpdate — a pessimistic write lock held for the rest of the
	 * caller's transaction, serializing concurrent check-then-insert races (professional/branch/
	 * service/weekly-appointment plan limits) per tenant, without contending across tenants. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Tenant t where t.id = :id")
	Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);

	/** Backs TrialExpirationScheduler — {@code trialExpiresAt} is null for every tenant that
	 * existed before that feature shipped, so this naturally never matches them. */
	List<Tenant> findByPlanTierAndTrialExpiresAtBefore(PlanTier planTier, Instant cutoff);
}
