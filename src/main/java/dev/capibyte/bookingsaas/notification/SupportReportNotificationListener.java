package dev.capibyte.bookingsaas.notification;

import dev.capibyte.bookingsaas.support.SupportReportSubmittedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so the founder is never emailed about a report whose insert then rolled back. */
@Component
public class SupportReportNotificationListener {

	private final MailService mailService;
	private final String founderEmail;

	public SupportReportNotificationListener(MailService mailService,
			@Value("${app.support.founder-email}") String founderEmail) {
		this.mailService = mailService;
		this.founderEmail = founderEmail;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSupportReportSubmitted(SupportReportSubmittedEvent event) {
		String body = "De: " + event.submitterEmail() + " (" + event.tenantName() + ")\n\n" + event.message();
		mailService.sendWithAttachment(founderEmail, "Reporte de error — " + event.tenantName(), body,
				event.imagePath(), event.imageContentType());
	}
}
