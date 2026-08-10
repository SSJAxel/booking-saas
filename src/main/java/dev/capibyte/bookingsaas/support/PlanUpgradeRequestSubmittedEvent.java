package dev.capibyte.bookingsaas.support;

/**
 * Carries display-ready data (not just the report id) for the same reason as
 * SupportReportSubmittedEvent: the listener runs AFTER_COMMIT, by which point TenantContext has
 * already been cleared, so it can't re-resolve the tenant name/slug or submitter email itself.
 */
public record PlanUpgradeRequestSubmittedEvent(
		String tenantName,
		String tenantSlug,
		String submitterEmail,
		String message) {
}
