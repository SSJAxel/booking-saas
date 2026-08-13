package dev.capibyte.bookingsaas.admin.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import java.math.BigDecimal;
import java.util.UUID;

/** Usage over the last 30 days, sorted by {@code revenue} desc — highest-usage tenants are the
 * best upsell candidates. {@code revenue} sums the price of COMPLETED appointments only, same
 * definition as ReportService's per-tenant revenue figure. */
public record TenantUsageResponse(
		UUID tenantId,
		String name,
		PlanTier planTier,
		long professionalCount,
		long branchCount,
		long stockUnits,
		long serviceCount,
		long appointmentCount,
		BigDecimal revenue) {
}
