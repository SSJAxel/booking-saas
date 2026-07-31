package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import java.math.BigDecimal;

public record PlanCatalogResponse(String tier, BigDecimal monthlyPrice, Integer maxProducts) {

	public static PlanCatalogResponse from(PlanTier tier) {
		return new PlanCatalogResponse(tier.name(), tier.getMonthlyPrice(), tier.getMaxProducts());
	}
}
