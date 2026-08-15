package dev.capibyte.bookingsaas.waitlist;

import dev.capibyte.bookingsaas.notification.MailService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT for the same reason as AppointmentNotificationListener — see its Javadoc. */
@Component
@RequiredArgsConstructor
public class WaitlistNotificationListener {

	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", new Locale("es", "AR"));

	private final MailService mailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWaitlistSlotAvailable(WaitlistNotificationEvent event) {
		mailService.send(event.clientEmail(), "Se liberó un turno",
				"Hola " + event.clientName() + ",\n\nSe liberó un lugar para \"" + event.serviceName() + "\" con "
						+ event.professionalName() + " el " + DATE_FORMAT.format(event.date())
						+ ". Reservá pronto, ¡puede que no dure!");
	}
}
