package dev.capibyte.bookingsaas.support;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.FileStorageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SupportReportService {

	private static final String UPLOAD_SUBDIR = "support-reports";

	private final SupportReportRepository supportReportRepository;
	private final FileStorageService fileStorageService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public SupportReport create(String message, MultipartFile image, UUID appUserId, String submitterEmail,
			String tenantName) {
		if (message == null || message.isBlank()) {
			throw new BadRequestException("A message is required");
		}
		String relativePath = fileStorageService.store(image, UPLOAD_SUBDIR);

		SupportReport report = new SupportReport();
		report.setType(SupportReportType.BUG);
		report.setMessage(message);
		report.setImagePath(relativePath);
		report.setImageContentType(image.getContentType());
		report.setAppUserId(appUserId);
		report = supportReportRepository.save(report);

		eventPublisher.publishEvent(new SupportReportSubmittedEvent(tenantName, submitterEmail, message,
				fileStorageService.resolve(relativePath), image.getContentType()));
		return report;
	}

	/** "Mejorar plan" — no screenshot, and the founder reaches out and grants the plan by hand from
	 * the super-admin panel (PlatformAdminController#updateTenantPlan) rather than an automated
	 * checkout, since MAX has no self-service price yet and a custom deal needs a conversation. */
	@Transactional
	public SupportReport createPlanUpgradeRequest(String note, UUID appUserId, String submitterEmail,
			String tenantName, String tenantSlug) {
		SupportReport report = new SupportReport();
		report.setType(SupportReportType.PLAN_UPGRADE);
		report.setMessage(note == null || note.isBlank() ? "Quiere mejorar su plan." : note);
		report.setAppUserId(appUserId);
		report = supportReportRepository.save(report);

		eventPublisher.publishEvent(
				new PlanUpgradeRequestSubmittedEvent(tenantName, tenantSlug, submitterEmail, report.getMessage()));
		return report;
	}
}
