package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the two tiers and their limits
 * are fixed for now — promote to an entity if plans ever need to be editable at runtime (e.g. an
 * admin creating custom tiers) instead of shipped in code.
 */
public enum PlanTier {

	BASIC(5, BigDecimal.ZERO),
	// ARS 23.000 ≈ USD 15/mes al dólar blue de referencia (2026-08-01, ~$1.560) — calculado contra
	// el costo real de hosting (~USD 35/mes en Render) y el piso de precios de AgendaPro (USD 19),
	// ver README → "Comercialización y hosting". Fijo en pesos por ahora; revisar a mano si el tipo
	// de cambio se mueve mucho, no hay indexación automática construida.
	PRO(null, new BigDecimal("23000.00"));

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
