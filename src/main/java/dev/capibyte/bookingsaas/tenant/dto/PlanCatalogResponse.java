package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import java.math.BigDecimal;

/** {@code monthlyPrice} is resolved by {@code PlanPricingService#currentPrice}, not read straight
 * off the enum — see {@link PlanTier}'s Javadoc for why. {@code extraProfessionalPrice} is the
 * reference number for what the founder charges to approve more employees on this tier by hand
 * (not a self-service charge). */
public record PlanCatalogResponse(String tier, BigDecimal monthlyPrice, int maxProfessionals, Integer maxProducts,
		int maxBranches, int maxServices, Integer maxAppointmentsPerWeek, boolean mercadoPagoEnabled,
		boolean whatsappEnabled, BigDecimal extraProfessionalPrice) {

	public static PlanCatalogResponse from(PlanTier tier, BigDecimal currentPrice) {
		return new PlanCatalogResponse(tier.name(), currentPrice, tier.getMaxProfessionals(), tier.getMaxProducts(),
				tier.getMaxBranches(), tier.getMaxServices(), tier.getMaxAppointmentsPerWeek(),
				tier.isMercadoPagoEnabled(), tier.isWhatsappEnabled(), tier.getExtraProfessionalPrice());
	}
}
