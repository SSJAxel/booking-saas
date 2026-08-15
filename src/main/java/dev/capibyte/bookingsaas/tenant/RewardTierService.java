package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RewardTierService {

	private static final int MAX_TIERS = 5;

	private final RewardTierRepository rewardTierRepository;
	private final TenantService tenantService;

	/** Locks the tenant row for the count-then-insert check, same reasoning and same reused
	 * mechanism as ProfessionalService/BranchService/ServiceOfferingService#create — without it two
	 * concurrent requests could both read "4 tiers" and both insert a 5th. */
	@Transactional
	public RewardTier create(int pointsRequired, String description) {
		Tenant tenant = tenantService.findByIdForUpdate(TenantContext.getTenantId());
		if (rewardTierRepository.count() >= MAX_TIERS) {
			throw new BadRequestException("A tenant can have at most " + MAX_TIERS + " reward tiers");
		}
		validatePointsRequired(tenant, pointsRequired);
		RewardTier tier = new RewardTier();
		tier.setPointsRequired(pointsRequired);
		tier.setDescription(description);
		return rewardTierRepository.save(tier);
	}

	@Transactional
	public RewardTier update(UUID id, int pointsRequired, String description) {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		validatePointsRequired(tenant, pointsRequired);
		RewardTier tier = findById(id);
		tier.setPointsRequired(pointsRequired);
		tier.setDescription(description);
		return tier;
	}

	@Transactional
	public void delete(UUID id) {
		if (!rewardTierRepository.existsById(id)) {
			throw new NotFoundException("Reward tier not found: " + id);
		}
		rewardTierRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<RewardTier> findAll() {
		return rewardTierRepository.findAllByOrderByPointsRequiredAsc();
	}

	@Transactional(readOnly = true)
	public RewardTier findById(UUID id) {
		return rewardTierRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Reward tier not found: " + id));
	}

	/** A tier requiring more points than the tenant's own cap could never be reached — same
	 * unreachable-tier guard as TenantService#updateLoyaltyRewardsSettings, mirrored here for the
	 * opposite direction (tier created/edited after the cap, instead of cap lowered after the tier). */
	private void validatePointsRequired(Tenant tenant, int pointsRequired) {
		if (pointsRequired > tenant.getLoyaltyPointsCap()) {
			throw new BadRequestException(
					"pointsRequired can't be higher than the tenant's loyaltyPointsCap (" + tenant.getLoyaltyPointsCap()
							+ ")");
		}
	}
}
