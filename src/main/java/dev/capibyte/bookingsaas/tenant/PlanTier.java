package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the two tiers and their limits
 * are fixed for now — promote to an entity if plans ever need to be editable at runtime (e.g. an
 * admin creating custom tiers) instead of shipped in code.
 */
public enum PlanTier {

	// Every tenant's plan right now, while pricing/limits for the tiers below aren't decided yet —
	// everything unlocked (no product cap), not billed. See README roadmap.
	TRIAL(null, BigDecimal.ZERO),
	BASIC(5, BigDecimal.ZERO),
	// ARS 23.000 ≈ USD 15/mes al dólar blue de referencia (2026-08-01, ~$1.560) — calculado contra
	// el costo real de hosting (~USD 35/mes en Render) y el piso de precios de AgendaPro (USD 19),
	// ver README → "Comercialización y hosting". Fijo en pesos por ahora; revisar a mano si el tipo
	// de cambio se mueve mucho, no hay indexación automática construida.
	PRO(null, new BigDecimal("23000.00")),
	// Price genuinely undecided (null, not zero) — exists so the plans list can show it as
	// "próximamente" ahead of time. subscribe() rejects it explicitly until a real price is set.
	MAX(null, null);

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

	/** Null means not priced yet (see MAX) — distinct from free (zero). */
	public BigDecimal getMonthlyPrice() {
		return monthlyPrice;
	}

	public boolean isFree() {
		return monthlyPrice != null && monthlyPrice.signum() == 0;
	}
}
