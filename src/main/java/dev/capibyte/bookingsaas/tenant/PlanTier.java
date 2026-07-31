package dev.capibyte.bookingsaas.tenant;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the two tiers and their limits
 * are fixed for now — promote to an entity if plans ever need to be editable at runtime (e.g. an
 * admin creating custom tiers) instead of shipped in code.
 */
public enum PlanTier {

	BASIC(5),
	PRO(null);

	private final Integer maxProducts;

	PlanTier(Integer maxProducts) {
		this.maxProducts = maxProducts;
	}

	/** Null means no limit. */
	public Integer getMaxProducts() {
		return maxProducts;
	}
}
