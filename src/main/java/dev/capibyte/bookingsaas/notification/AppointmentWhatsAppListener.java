package dev.capibyte.bookingsaas.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * Second, independent listener alongside {@link AppointmentNotificationListener} — WhatsApp is an
 * extra channel on top of email, never a replacement for it, so this only fires when the tenant
 * opted in ({@code event.whatsappEnabled()}) and the client actually gave a phone number.
 * Spanish copy, distinct from the English-language email listener, since WhatsApp in this market
 * is aimed at LatAm end clients rather than the (English-drafted) admin-facing email templates.
 */
@Component
public class AppointmentWhatsAppListener {

	private static final DateTimeFormatter WHEN_FORMAT =
			DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", new Locale("es", "AR"));

	private final WhatsAppNotificationService whatsAppNotificationService;

	public AppointmentWhatsAppListener(WhatsAppNotificationService whatsAppNotificationService) {
		this.whatsAppNotificationService = whatsAppNotificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAppointmentNotification(AppointmentNotificationEvent event) {
		if (!event.whatsappEnabled() || !StringUtils.hasText(event.clientPhone())) {
			return;
		}
		String when = WHEN_FORMAT.withZone(event.zone()).format(event.startTime());
		String message = switch (event.status()) {
			case PENDING -> "Hola " + event.clientName() + "! Recibimos tu reserva de \"" + event.serviceName()
					+ "\" con " + event.professionalName() + " para el " + when + ". Queda pendiente de confirmacion.";
			case CONFIRMED -> "Hola " + event.clientName() + "! Tu turno de \"" + event.serviceName() + "\" con "
					+ event.professionalName() + " para el " + when + " fue confirmado.";
			case CANCELLED -> "Hola " + event.clientName() + ", tu turno de \"" + event.serviceName() + "\" con "
					+ event.professionalName() + " para el " + when + " fue cancelado.";
			case COMPLETED, NO_SHOW -> null;
		};
		if (message != null) {
			whatsAppNotificationService.send(event.clientPhone(), message);
		}
	}
}
