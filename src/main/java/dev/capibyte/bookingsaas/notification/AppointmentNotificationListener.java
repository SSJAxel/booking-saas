package dev.capibyte.bookingsaas.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT (not the default synchronous-with-the-transaction phase) so a client is never
 * emailed about a booking that then got rolled back — e.g. if the EXCLUDE constraint had won the
 * race after this event was queued but before commit. COMPLETED/NO_SHOW intentionally don't
 * trigger a client-facing email.
 */
@Component
@RequiredArgsConstructor
public class AppointmentNotificationListener {

	// Locale pinned explicitly — DateTimeFormatter otherwise falls back to the JVM's default
	// locale, which would silently mix Spanish month/day names into this English-language email
	// on any host whose default locale isn't English (verified: happened on this very machine).
	// No zone baked in here (unlike before this carried per-tenant zones) — event.zone() supplies
	// it per email, via withZone() below, since different tenants can be in different zones.
	private static final DateTimeFormatter WHEN_FORMAT =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

	private final MailService mailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAppointmentNotification(AppointmentNotificationEvent event) {
		String when = WHEN_FORMAT.withZone(event.zone()).format(event.startTime()) + " (" + event.zone() + ")";
		switch (event.status()) {
			case PENDING -> mailService.send(event.clientEmail(), "Booking received",
					greeting(event) + "We received your booking for \"" + event.serviceName() + "\" with "
							+ event.professionalName() + " on " + when + ". It's pending confirmation.");
			case CONFIRMED -> mailService.send(event.clientEmail(), "Booking confirmed",
					greeting(event) + "Your appointment for \"" + event.serviceName() + "\" with "
							+ event.professionalName() + " on " + when + " has been confirmed.");
			case CANCELLED -> mailService.send(event.clientEmail(), "Booking cancelled",
					greeting(event) + "Your appointment for \"" + event.serviceName() + "\" with "
							+ event.professionalName() + " on " + when + " has been cancelled.");
			case COMPLETED, NO_SHOW -> {
				// No client-facing email for these — they're operational statuses, not booking updates.
			}
		}
	}

	private String greeting(AppointmentNotificationEvent event) {
		return "Hi " + event.clientName() + ",\n\n";
	}
}
