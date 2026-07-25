package dev.capibyte.bookingsaas.waitlist;

import java.time.LocalDate;

public record WaitlistNotificationEvent(
		String clientEmail,
		String clientName,
		String professionalName,
		String serviceName,
		LocalDate date) {
}
