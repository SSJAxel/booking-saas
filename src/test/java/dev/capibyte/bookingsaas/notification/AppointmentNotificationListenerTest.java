package dev.capibyte.bookingsaas.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AppointmentNotificationListenerTest {

	private final MailService mailService = mock(MailService.class);
	private final AppointmentNotificationListener listener = new AppointmentNotificationListener(mailService);

	@Test
	void pendingBookingSendsAReceivedEmail() {
		listener.onAppointmentNotification(event(AppointmentStatus.PENDING));

		verify(mailService).send(eq("client@example.com"), contains("received"), anyString());
	}

	@Test
	void confirmedSendsAConfirmationEmail() {
		listener.onAppointmentNotification(event(AppointmentStatus.CONFIRMED));

		verify(mailService).send(eq("client@example.com"), contains("confirmed"), anyString());
	}

	@Test
	void cancelledSendsACancellationEmail() {
		listener.onAppointmentNotification(event(AppointmentStatus.CANCELLED));

		verify(mailService).send(eq("client@example.com"), contains("cancelled"), anyString());
	}

	@Test
	void completedAndNoShowDoNotEmailTheClient() {
		listener.onAppointmentNotification(event(AppointmentStatus.COMPLETED));
		listener.onAppointmentNotification(event(AppointmentStatus.NO_SHOW));

		verifyNoInteractions(mailService);
	}

	private AppointmentNotificationEvent event(AppointmentStatus status) {
		return new AppointmentNotificationEvent("client@example.com", "Jane Doe", "Maria Tattoo", "Small Tattoo",
				Instant.parse("2026-08-10T10:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"), status);
	}
}
