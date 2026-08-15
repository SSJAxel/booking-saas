package dev.capibyte.bookingsaas.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardTierRepository extends JpaRepository<RewardTier, UUID> {

	List<RewardTier> findAllByOrderByPointsRequiredAsc();

	/** Backs the "would this cap change strand an existing tier?" check in
	 * TenantService#updateLoyaltyRewardsSettings. */
	boolean existsByPointsRequiredGreaterThan(int pointsCap);
}
