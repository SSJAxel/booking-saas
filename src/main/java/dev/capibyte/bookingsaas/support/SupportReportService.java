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
		report.setMessage(message);
		report.setImagePath(relativePath);
		report.setImageContentType(image.getContentType());
		report.setAppUserId(appUserId);
		report = supportReportRepository.save(report);

		eventPublisher.publishEvent(new SupportReportSubmittedEvent(tenantName, submitterEmail, message,
				fileStorageService.resolve(relativePath), image.getContentType()));
		return report;
	}
}
