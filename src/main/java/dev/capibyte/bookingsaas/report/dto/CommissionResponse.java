package dev.capibyte.bookingsaas.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One professional's commission breakdown for a period — GET /api/reports/commissions. Revenue
 * figures are the raw amounts commission was calculated against (service prices at their current
 * value, product sale amounts as snapshotted at sale time — see ReportService#commissions), the
 * commission fields are the professional's cut of each. */
public record CommissionResponse(
		UUID professionalId,
		String professionalName,
		BigDecimal serviceRevenue,
		BigDecimal serviceCommission,
		BigDecimal productRevenue,
		BigDecimal productCommission,
		BigDecimal totalCommission) {
}
