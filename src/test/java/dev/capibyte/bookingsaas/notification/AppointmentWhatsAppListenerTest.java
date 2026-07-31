package dev.capibyte.bookingsaas.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AppointmentWhatsAppListenerTest {

	private final WhatsAppNotificationService whatsAppNotificationService = mock(WhatsAppNotificationService.class);
	private final AppointmentWhatsAppListener listener = new AppointmentWhatsAppListener(whatsAppNotificationService);

	@Test
	void sendsAWhatsAppMessageWhenEnabledAndPhonePresent() {
		listener.onAppointmentNotification(event(AppointmentStatus.CONFIRMED, true, "+5491122334455"));

		verify(whatsAppNotificationService).send(eq("+5491122334455"), anyString());
	}

	@Test
	void doesNothingWhenTenantHasNotOptedIn() {
		listener.onAppointmentNotification(event(AppointmentStatus.CONFIRMED, false, "+5491122334455"));

		verifyNoInteractions(whatsAppNotificationService);
	}

	@Test
	void doesNothingWhenClientHasNoPhone() {
		listener.onAppointmentNotification(event(AppointmentStatus.CONFIRMED, true, null));

		verifyNoInteractions(whatsAppNotificationService);
	}

	@Test
	void completedAndNoShowDoNotMessageTheClient() {
		listener.onAppointmentNotification(event(AppointmentStatus.COMPLETED, true, "+5491122334455"));
		listener.onAppointmentNotification(event(AppointmentStatus.NO_SHOW, true, "+5491122334455"));

		verifyNoInteractions(whatsAppNotificationService);
	}

	private AppointmentNotificationEvent event(AppointmentStatus status, boolean whatsappEnabled, String clientPhone) {
		return new AppointmentNotificationEvent("client@example.com", "Jane Doe", "Maria Tattoo", "Small Tattoo",
				Instant.parse("2026-08-10T10:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"), status,
				whatsappEnabled, clientPhone);
	}
}
