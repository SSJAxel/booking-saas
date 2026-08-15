package dev.capibyte.bookingsaas.report;

import dev.capibyte.bookingsaas.report.dto.ClientStatsResponse;
import dev.capibyte.bookingsaas.report.dto.CommissionResponse;
import dev.capibyte.bookingsaas.report.dto.DailyCountResponse;
import dev.capibyte.bookingsaas.report.dto.DailySalesResponse;
import dev.capibyte.bookingsaas.report.dto.ReportSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/summary")
	public ReportSummaryResponse summary(
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID professionalId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return reportService.summarize(branchId, professionalId, from, to);
	}

	@GetMapping("/today")
	public ReportSummaryResponse today() {
		return reportService.summarizeToday();
	}

	@GetMapping("/traffic")
	public List<DailyCountResponse> traffic(@RequestParam(defaultValue = "7") int days) {
		return reportService.dailyAppointmentCounts(days);
	}

	@GetMapping("/product-sales")
	public List<DailySalesResponse> productSales(@RequestParam(defaultValue = "7") int days) {
		return reportService.dailySales(days);
	}

	@GetMapping("/clients")
	public List<ClientStatsResponse> clients() {
		return reportService.clientStats();
	}

	@GetMapping("/commissions")
	public List<CommissionResponse> commissions(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return reportService.commissions(from, to);
	}
}
