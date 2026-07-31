package dev.capibyte.bookingsaas.notification;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Deliberately carries display-ready data (names, not IDs) instead of just the appointment ID —
 * publishing it inside AppointmentService means the data is already loaded in that transaction,
 * so the listener needs zero extra queries and never has to worry about tenant-context timing
 * relative to the transaction that published it. {@code zone} is carried for the same reason —
 * the listener runs AFTER_COMMIT (see AppointmentNotificationListener), by which point
 * TenantContext has already been cleared, so it can't re-resolve the tenant's zone itself.
 */
public record AppointmentNotificationEvent(
		String clientEmail,
		String clientName,
		String professionalName,
		String serviceName,
		Instant startTime,
		ZoneId zone,
		AppointmentStatus status) {
}
