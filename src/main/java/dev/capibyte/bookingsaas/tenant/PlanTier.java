package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;

/**
 * Commercial tiers. Kept as an enum (not a DB-backed entity) since the tiers' limits and feature
 * flags are fixed for now — promote to an entity if plans ever need to be editable at runtime
 * (e.g. an admin creating custom tiers) instead of shipped in code.
 *
 * <p>{@code monthlyPrice} is the exception: for PERSONAL/BASIC/PRO/MAX it's deliberately null —
 * their real list price now lives in the {@code plan_pricing} table (see {@code PlanPricing},
 * {@code PlanPricingService}) so {@code PlanPricingScheduler} can index it to the dólar blue
 * without a redeploy. Only TRIAL keeps a hardcoded price (always {@link BigDecimal#ZERO} — a
 * genuinely free demo tier, never indexed).
 *
 * <p>Matriz de límites (Integer nullable = sin límite, igual que ya hacía maxProducts).
 * {@code maxProfessionals} es la cantidad de empleados *incluida por defecto* en el precio base,
 * no un techo técnico duro — un tenant puntual puede tener más vía
 * {@code Tenant#professionalLimitOverride}, fijado a mano por el founder cuando aprueba un pedido
 * de "mejorar plan" por más empleados dentro del mismo tier (ver {@code ProfessionalService}).
 * {@code extraProfessionalPrice} es solo un número de referencia para esa negociación manual —
 * ninguna lógica de cobro automática lo usa (los upgrades de empleados no son self-service).
 * <pre>
 * Plan      maxProf(incl)  maxProducts  maxBranches  maxServices  maxAppt/semana  MP     WhatsApp  Loyalty  Commissions  Reviews  extraProfPrice
 * TRIAL         2               5            2            6            null      false   true      false    false        false    —
 * PERSONAL      1               0            1            3            20        false   false     false    false        false    —
 * BASIC         2               5            2            6            null      false   true      false    false        false    3000.00
 * PRO           5              10            3            8            null      true    true      true     true         true     2500.00
 * MAX          10              20            4           12            null      true    true      true     true         true     2000.00
 * </pre>
 * {@code loyaltyRewardsEnabled}/{@code commissionsEnabled}/{@code reviewsEnabled} (all PRO/MAX
 * only) gate {@code Tenant#loyaltyRewardsEnabled}/{@code Tenant#commissionsEnabled}/
 * {@code Tenant#reviewsEnabled} — same "premium differentiator" tier split as
 * {@code mercadoPagoEnabled}, per the founder's own call when these features were designed
 * (2026-08-14 WhatsApp thread relaying a competitor's client's feature requests).
 * TRIAL ("Demo") replica los límites operativos de BASIC (mismo maxProfessionals/maxBranches) —
 * la única diferencia real es el precio: TRIAL es el único plan genuinamente gratis. El tope de
 * turnos por semana es EXCLUSIVO de PERSONAL.
 */
public enum PlanTier {

	TRIAL(2, 5, 2, 6, null, false, true, false, false, false, BigDecimal.ZERO, null),
	// Mismos límites operativos que BASIC (ver Javadoc de la clase) — precio genuinamente gratis,
	// el único tier que lo es de verdad.
	PERSONAL(1, 0, 1, 3, 20, false, false, false, false, false, null, null),
	// Escalón de entrada, un solo profesional, sin stock ni Mercado Pago ni WhatsApp — el único
	// tope de turnos/semana de toda la matriz vive acá. Sin cargo por empleado extra: no hay
	// upgrade de cantidad dentro de este tier, directamente se sube a BASIC.
	BASIC(2, 5, 2, 6, null, false, true, false, false, false, null, new BigDecimal("3000.00")),
	// Matriz de precios 2026-08-11: base ARS 30.000, 2 empleados incluidos, $3.000 por cada
	// empleado extra aprobado a mano por el founder.
	PRO(5, 10, 3, 8, null, true, true, true, true, true, null, new BigDecimal("2500.00")),
	// Base ARS 50.000, 5 empleados incluidos, $2.500 por extra — más barato que el de BASIC
	// ($3.000) a propósito, "descuento por volumen": un negocio grande no debería pagar
	// proporcionalmente más que uno chico por el mismo empleado adicional.
	MAX(10, 20, 4, 12, null, true, true, true, true, true, null, new BigDecimal("2000.00"));
	// Base ARS 80.000, 10 empleados incluidos, $2.000 por extra. Sin techo técnico de empleados
	// en código — los casos grandes se negocian con el founder aparte (integración a medida).

	private final int maxProfessionals;
	private final Integer maxProducts;
	private final int maxBranches;
	private final int maxServices;
	private final Integer maxAppointmentsPerWeek;
	private final boolean mercadoPagoEnabled;
	private final boolean whatsappEnabled;
	private final boolean loyaltyRewardsEnabled;
	private final boolean commissionsEnabled;
	private final boolean reviewsEnabled;
	private final BigDecimal monthlyPrice;
	private final BigDecimal extraProfessionalPrice;

	PlanTier(int maxProfessionals, Integer maxProducts, int maxBranches, int maxServices,
			Integer maxAppointmentsPerWeek, boolean mercadoPagoEnabled, boolean whatsappEnabled,
			boolean loyaltyRewardsEnabled, boolean commissionsEnabled, boolean reviewsEnabled,
			BigDecimal monthlyPrice, BigDecimal extraProfessionalPrice) {
		this.maxProfessionals = maxProfessionals;
		this.maxProducts = maxProducts;
		this.maxBranches = maxBranches;
		this.maxServices = maxServices;
		this.maxAppointmentsPerWeek = maxAppointmentsPerWeek;
		this.mercadoPagoEnabled = mercadoPagoEnabled;
		this.whatsappEnabled = whatsappEnabled;
		this.loyaltyRewardsEnabled = loyaltyRewardsEnabled;
		this.commissionsEnabled = commissionsEnabled;
		this.reviewsEnabled = reviewsEnabled;
		this.monthlyPrice = monthlyPrice;
		this.extraProfessionalPrice = extraProfessionalPrice;
	}

	/** Empleados incluidos por defecto en el precio base — no es un techo duro, ver Javadoc de la
	 * clase y {@code Tenant#getEffectiveProfessionalLimit()}. */
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

	public boolean isLoyaltyRewardsEnabled() {
		return loyaltyRewardsEnabled;
	}

	public boolean isCommissionsEnabled() {
		return commissionsEnabled;
	}

	public boolean isReviewsEnabled() {
		return reviewsEnabled;
	}

	/** Solo tiene un valor real para TRIAL (siempre gratis). Para PERSONAL/BASIC/PRO/MAX es null
	 * a propósito — su precio real vive en {@code plan_pricing}, resuelto por
	 * {@code PlanPricingService#currentPrice}, no acá. Ver Javadoc de la clase. */
	public BigDecimal getMonthlyPrice() {
		return monthlyPrice;
	}

	/** Número de referencia para que el founder decida cuánto cobrar cuando aprueba a mano un
	 * pedido de más empleados dentro de este tier — no lo aplica ninguna lógica automática. Null
	 * para TRIAL/PERSONAL (no hay upgrade de cantidad dentro de esos tiers). */
	public BigDecimal getExtraProfessionalPrice() {
		return extraProfessionalPrice;
	}

	public boolean isFree() {
		return monthlyPrice != null && monthlyPrice.signum() == 0;
	}
}
