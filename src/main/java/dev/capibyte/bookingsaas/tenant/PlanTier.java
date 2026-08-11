package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the tiers and their limits are
 * fixed for now — promote to an entity if plans ever need to be editable at runtime (e.g. an admin
 * creating custom tiers) instead of shipped in code.
 *
 * <p>Matriz de límites (Integer nullable = sin límite, igual que ya hacía maxProducts):
 * <pre>
 * Plan      maxProf  maxProducts  maxBranches  maxServices  maxAppt/semana  MP     WhatsApp  precio
 * TRIAL         4         5            2            6            null      false   true      0.00 (gratis)
 * PERSONAL      1         0            1            3            20        false   false     null (próx.)
 * BASIC         4         5            2            6            null      false   true      null (próx.)
 * PRO          10        10            4            8            null      true    true      23000.00
 * MAX          20        20            8           12            null      true    true      null (próx.)
 * </pre>
 * TRIAL ("Demo") tiene EXACTAMENTE los mismos límites operativos que BASIC — la única diferencia es
 * el precio: TRIAL es el único plan realmente gratis, BASIC ya no lo es (queda "próximamente", igual
 * que PERSONAL y MAX). El tope de turnos por semana es EXCLUSIVO de PERSONAL.
 */
public enum PlanTier {

	TRIAL(4, 5, 2, 6, null, false, true, BigDecimal.ZERO),
	// Escalón de entrada, un solo profesional, sin stock ni Mercado Pago ni WhatsApp — el único
	// tope de turnos/semana de toda la matriz vive acá. Precio genuinamente indefinido todavía
	// (null, no cero): no es gratis, "próximamente" como BASIC/MAX.
	PERSONAL(1, 0, 1, 3, 20, false, false, null),
	// Ya NO es gratis (antes BigDecimal.ZERO) — "ya no hay nada gratis, solo Demo que es de
	// prueba". Mismos límites operativos que TRIAL; precio todavía sin definir.
	BASIC(4, 5, 2, 6, null, false, true, null),
	// ARS 23.000 ≈ USD 15/mes al dólar blue de referencia (2026-08-01, ~$1.560) — calculado contra
	// el costo real de hosting (~USD 35/mes en Render) y el piso de precios de AgendaPro (USD 19),
	// ver README → "Comercialización y hosting". Fijo en pesos por ahora; revisar a mano si el tipo
	// de cambio se mueve mucho, no hay indexación automática construida.
	PRO(10, 10, 4, 8, null, true, true, new BigDecimal("23000.00")),
	// Price genuinely undecided (null, not zero) — exists so the plans list can show it as
	// "próximamente" ahead of time. subscribe() rejects it explicitly until a real price is set.
	MAX(20, 20, 8, 12, null, true, true, null);

	private final int maxProfessionals;
	private final Integer maxProducts;
	private final int maxBranches;
	private final int maxServices;
	private final Integer maxAppointmentsPerWeek;
	private final boolean mercadoPagoEnabled;
	private final boolean whatsappEnabled;
	private final BigDecimal monthlyPrice;

	PlanTier(int maxProfessionals, Integer maxProducts, int maxBranches, int maxServices,
			Integer maxAppointmentsPerWeek, boolean mercadoPagoEnabled, boolean whatsappEnabled,
			BigDecimal monthlyPrice) {
		this.maxProfessionals = maxProfessionals;
		this.maxProducts = maxProducts;
		this.maxBranches = maxBranches;
		this.maxServices = maxServices;
		this.maxAppointmentsPerWeek = maxAppointmentsPerWeek;
		this.mercadoPagoEnabled = mercadoPagoEnabled;
		this.whatsappEnabled = whatsappEnabled;
		this.monthlyPrice = monthlyPrice;
	}

	public int getMaxProfessionals() {
		return maxProfessionals;
	}

	/** Null means no limit. */
	public Integer getMaxProducts() {
		return maxProducts;
	}

	public int getMaxBranches() {
		return maxBranches;
	}

	public int getMaxServices() {
		return maxServices;
	}

	/** Null means no limit — every tier except PERSONAL. */
	public Integer getMaxAppointmentsPerWeek() {
		return maxAppointmentsPerWeek;
	}

	public boolean isMercadoPagoEnabled() {
		return mercadoPagoEnabled;
	}

	public boolean isWhatsappEnabled() {
		return whatsappEnabled;
	}

	/** Null means not priced yet (see PERSONAL/BASIC/MAX) — distinct from free (zero, see TRIAL). */
	public BigDecimal getMonthlyPrice() {
		return monthlyPrice;
	}

	public boolean isFree() {
		return monthlyPrice != null && monthlyPrice.signum() == 0;
	}
}
