package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of truth for "how much does a plan tier / tenant currently cost" — replaces what
 * used to be {@code PlanTier.getMonthlyPrice()} (still there, but null for every priced tier now,
 * see its Javadoc) and the old {@code Tenant.getEffectiveMonthlyPrice()} (removed — needs a DB
 * read now, doesn't belong on the entity anymore).
 */
@Service
@RequiredArgsConstructor
public class PlanPricingService {

	private final PlanPricingRepository planPricingRepository;

	/** The tier's current list price. TRIAL (and any tier with no {@code plan_pricing} row) falls
	 * back to {@link PlanTier#getMonthlyPrice()} — TRIAL's is a hardcoded {@link BigDecimal#ZERO},
	 * never indexed. */
	@Transactional(readOnly = true)
	public BigDecimal currentPrice(PlanTier tier) {
		return planPricingRepository.findById(tier).map(PlanPricing::getArsPrice).orElseGet(tier::getMonthlyPrice);
	}

	/** What a specific tenant actually bills — its own negotiated override if the founder set
	 * one, otherwise its plan tier's current list price. */
	@Transactional(readOnly = true)
	public BigDecimal effectivePrice(Tenant tenant) {
		return tenant.getCustomMonthlyPrice() != null ? tenant.getCustomMonthlyPrice()
				: currentPrice(tenant.getPlanTier());
	}
}
