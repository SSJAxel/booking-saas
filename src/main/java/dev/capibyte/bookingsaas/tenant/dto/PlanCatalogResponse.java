package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import java.math.BigDecimal;

public record PlanCatalogResponse(String tier, BigDecimal monthlyPrice, int maxProfessionals, Integer maxProducts,
		int maxBranches, int maxServices, Integer maxAppointmentsPerWeek, boolean mercadoPagoEnabled,
		boolean whatsappEnabled) {

	public static PlanCatalogResponse from(PlanTier tier) {
		return new PlanCatalogResponse(tier.name(), tier.getMonthlyPrice(), tier.getMaxProfessionals(),
				tier.getMaxProducts(), tier.getMaxBranches(), tier.getMaxServices(),
				tier.getMaxAppointmentsPerWeek(), tier.isMercadoPagoEnabled(), tier.isWhatsappEnabled());
	}
}
