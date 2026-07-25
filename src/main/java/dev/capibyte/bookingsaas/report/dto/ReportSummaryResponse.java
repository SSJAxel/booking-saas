package dev.capibyte.bookingsaas.report.dto;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReportSummaryResponse(
		long totalAppointments,
		Map<AppointmentStatus, Long> byStatus,
		BigDecimal revenue,
		BigDecimal depositsCollected,
		double noShowRate,
		List<ServiceCount> topServices,
		List<ProfessionalCount> topProfessionals) {
}
