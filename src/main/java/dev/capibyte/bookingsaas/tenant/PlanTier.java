package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the two tiers and their limits
 * are fixed for now — promote to an entity if plans ever need to be editable at runtime (e.g. an
 * admin creating custom tiers) instead of shipped in code.
 */
public enum PlanTier {

	BASIC(5, BigDecimal.ZERO),
	// Placeholder price — pick a real number (and currency strategy) before actually selling this.
	PRO(null, new BigDecimal("15000.00"));

	private final Integer maxProducts;
	private final BigDecimal monthlyPrice;

	PlanTier(Integer maxProducts, BigDecimal monthlyPrice) {
		this.maxProducts = maxProducts;
		this.monthlyPrice = monthlyPrice;
	}

	/** Null means no limit. */
	public Integer getMaxProducts() {
		return maxProducts;
	}

	public BigDecimal getMonthlyPrice() {
		return monthlyPrice;
	}

	public boolean isFree() {
		return monthlyPrice.signum() == 0;
	}
}
