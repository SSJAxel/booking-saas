package dev.capibyte.bookingsaas.notification;

/**
 * Same reasoning as {@link AppointmentNotificationEvent}: carries display-ready data (names, not
 * IDs) since it's published inside AppointmentService's transaction where they're already loaded,
 * and {@link ReviewInviteNotificationListener} runs AFTER_COMMIT — by which point TenantContext is
 * gone, so it can't re-resolve the tenant slug or re-fetch anything itself.
 */
public record ReviewInviteEvent(
		String clientEmail,
		String clientName,
		String professionalName,
		String serviceName,
		String tenantSlug,
		String token) {
}
