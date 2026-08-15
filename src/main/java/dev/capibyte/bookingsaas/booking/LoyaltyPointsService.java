package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.tenant.Tenant;
import org.springframework.stereotype.Service;

/**
 * Kept separate from {@link ClientRatingService} on purpose: rating is a pure entity mutation with
 * no tenant awareness today, and every visit affects it regardless of plan — loyalty points only
 * move for tenants that opted in (PRO/MAX, {@code Tenant#loyaltyRewardsEnabled}), so this needs the
 * tenant passed in. Conflating the two would force rating to gain a dependency it doesn't otherwise
 * need.
 */
@Service
public class LoyaltyPointsService {

	/** No-ops when the tenant hasn't turned loyalty rewards on — a tenant that enables it later
	 * shouldn't suddenly find clients with a confusing backdated balance. Clamped at
	 * {@code loyaltyPointsCap}: once a client is at the cap, further completed visits don't add more
	 * until they redeem something — see RewardTier's Javadoc for why that's the point. */
	public void awardPointForCompletedVisit(Tenant tenant, Client client) {
		if (!tenant.isLoyaltyRewardsEnabled()) {
			return;
		}
		client.setLoyaltyPoints(Math.min(tenant.getLoyaltyPointsCap(), client.getLoyaltyPoints() + 1));
	}
}
