package dev.capibyte.bookingsaas.admin.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import java.math.BigDecimal;
import java.util.Map;

/** Only tenants with {@code status == ACTIVE} count toward MRR — a suspended or
 * pending-approval tenant isn't actually being billed. {@code totalMrr}/{@code mrrByPlan} treat a
 * tenant with no priced plan yet (see PlanTier#getMonthlyPrice) as contributing zero. */
public record MrrSummaryResponse(
		BigDecimal totalMrr,
		Map<PlanTier, BigDecimal> mrrByPlan,
		Map<PlanTier, Long> tenantCountByPlan) {
}
