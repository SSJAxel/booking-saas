package dev.capibyte.bookingsaas.report;

import dev.capibyte.bookingsaas.report.dto.ReportSummaryResponse;
import java.time.Instant;
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
}
