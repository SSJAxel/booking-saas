package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.RewardTier;
import java.util.UUID;

public record RewardTierResponse(UUID id, int pointsRequired, String description) {

	public static RewardTierResponse from(RewardTier tier) {
		return new RewardTierResponse(tier.getId(), tier.getPointsRequired(), tier.getDescription());
	}
}
