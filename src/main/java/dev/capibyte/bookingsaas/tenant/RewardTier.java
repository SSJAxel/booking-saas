package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A reward the tenant defines by hand — "at X points, this reward unlocks." Up to 5 per tenant
 * (RewardTierService), never auto-generated. See {@code Client#loyaltyPoints} and
 * {@code booking.LoyaltyPointsService} for how points are earned. */
@Entity
@Table(name = "reward_tiers")
@Getter
@Setter
@NoArgsConstructor
public class RewardTier extends BaseTenantEntity {

	@Column(name = "points_required", nullable = false)
	private int pointsRequired;

	@Column(nullable = false)
	private String description;
}
