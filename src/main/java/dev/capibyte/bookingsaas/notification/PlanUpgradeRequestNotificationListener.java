package dev.capibyte.bookingsaas.notification;

import dev.capibyte.bookingsaas.support.PlanUpgradeRequestSubmittedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so the founder is never emailed about a request whose insert then rolled back —
 * same reasoning as SupportReportNotificationListener, just no attachment (a plan-upgrade request
 * has no screenshot). */
@Component
public class PlanUpgradeRequestNotificationListener {

	private final MailService mailService;
	private final String founderEmail;

	public PlanUpgradeRequestNotificationListener(MailService mailService,
			@Value("${app.support.founder-email}") String founderEmail) {
		this.mailService = mailService;
		this.founderEmail = founderEmail;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPlanUpgradeRequestSubmitted(PlanUpgradeRequestSubmittedEvent event) {
		String body = "Tenant: " + event.tenantName() + " (" + event.tenantSlug() + ")\n"
				+ "De: " + event.submitterEmail() + "\n\n" + event.message();
		mailService.send(founderEmail, "Quiere mejorar su plan — " + event.tenantName(), body);
	}
}
