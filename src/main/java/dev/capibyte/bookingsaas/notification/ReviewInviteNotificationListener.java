package dev.capibyte.bookingsaas.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT — same reasoning as {@link AppointmentNotificationListener}: a client is never
 * invited to review an appointment whose COMPLETED transition then got rolled back.
 */
@Component
public class ReviewInviteNotificationListener {

	private final MailService mailService;
	private final String reviewBaseUrl;

	public ReviewInviteNotificationListener(MailService mailService,
			@Value("${app.mail.review-base-url}") String reviewBaseUrl) {
		this.mailService = mailService;
		this.reviewBaseUrl = reviewBaseUrl;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReviewInvite(ReviewInviteEvent event) {
		String link = reviewBaseUrl + "?tenant=" + event.tenantSlug() + "&token=" + event.token();
		mailService.send(event.clientEmail(), "¿Cómo estuvo tu visita?",
				"Hola " + event.clientName() + ",\n\n¡Gracias por tu visita! Nos encantaría saber cómo estuvo tu \""
						+ event.serviceName() + "\" con " + event.professionalName()
						+ ". Si tenés un minuto, dejanos tu reseña acá:\n" + link
						+ "\n\nEsto es totalmente opcional, y el link vence en 30 días.");
	}
}
