package dev.capibyte.bookingsaas.support;

import java.nio.file.Path;

/**
 * Carries display-ready data (not just the report id) for the same reason as
 * AppointmentNotificationEvent: the listener runs AFTER_COMMIT, by which point TenantContext has
 * already been cleared, so it can't re-resolve the tenant name or submitter email itself.
 */
public record SupportReportSubmittedEvent(
		String tenantName,
		String submitterEmail,
		String message,
		Path imagePath,
		String imageContentType) {
}
