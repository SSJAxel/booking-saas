package dev.capibyte.bookingsaas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * A failure here must never break the caller's actual business operation (a booking, a status
 * change) — the appointment is already committed by the time this runs. So every failure is
 * caught and logged, never rethrown.
 */
@Component
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	public void send(String to, String subject, String body) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);
		try {
			mailSender.send(message);
		} catch (MailException ex) {
			log.warn("Failed to send email to {} (subject: {}): {}", to, subject, ex.getMessage());
		}
	}
}
