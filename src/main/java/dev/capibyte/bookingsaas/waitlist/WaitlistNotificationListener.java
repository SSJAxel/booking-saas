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

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

	private final MailService mailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWaitlistSlotAvailable(WaitlistNotificationEvent event) {
		mailService.send(event.clientEmail(), "A slot opened up",
				"Hi " + event.clientName() + ",\n\nA spot for \"" + event.serviceName() + "\" with "
						+ event.professionalName() + " on " + DATE_FORMAT.format(event.date())
						+ " just opened up. Book again soon — it may not last!");
	}
}
