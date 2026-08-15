package dev.capibyte.bookingsaas.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * A failure here must never break the caller's actual business operation (a booking, a status
 * change) — the appointment is already committed by the time this runs. So every failure is
 * caught and logged, never rethrown.
 *
 * <p>Two delivery paths: raw SMTP via {@link JavaMailSender} (what local dev uses against
 * MailHog), or Resend's HTTPS API when {@code app.mail.resend.api-key} is set. The API path
 * exists because several PaaS free tiers (Render's among them) silently drop outbound SMTP
 * connections — confirmed live via a {@code MailConnectException} timing out against
 * smtp.gmail.com:587 — while an HTTPS call on 443 goes through unblocked. See README "Enviar
 * mail real" for setup.
 */
@Component
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;
	private final RestClient resendClient;
	private final String resendApiKey;
	private final String fromAddress;

	public MailService(JavaMailSender mailSender, RestClient.Builder restClientBuilder,
			@Value("${app.mail.from}") String fromAddress,
			@Value("${app.mail.resend.api-key:}") String resendApiKey) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
		this.resendApiKey = resendApiKey;
		this.resendClient = restClientBuilder.baseUrl("https://api.resend.com").build();
	}

	/** @return true if the send didn't throw. Callers for whom delivery is merely a courtesy (an
	 * appointment confirmation, say) can ignore this — see the class Javadoc. Callers for whom the
	 * mail IS the operation (e.g. the founder's only alert that a new tenant needs review) should
	 * check it and log more loudly on failure, since a WARN here is easy to miss. */
	public boolean send(String to, String subject, String body) {
		subject = sanitizeSubject(subject);
		if (StringUtils.hasText(resendApiKey)) {
			return sendViaResend(to, subject, body, List.of());
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);
		try {
			mailSender.send(message);
			return true;
		} catch (MailException ex) {
			log.warn("Failed to send email to {} (subject: {}): {}", to, subject, ex.getMessage());
			return false;
		}
	}

	public void sendWithAttachment(String to, String subject, String body, Path attachmentPath, String contentType) {
		subject = sanitizeSubject(subject);
		if (StringUtils.hasText(resendApiKey)) {
			try {
				String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(attachmentPath));
				sendViaResend(to, subject, body, List.of(Map.of(
						"filename", attachmentPath.getFileName().toString(),
						"content", base64)));
			} catch (IOException ex) {
				log.warn("Failed to read attachment {} for email to {}: {}", attachmentPath, to, ex.getMessage());
			}
			return;
		}
		try {
			MimeMessage mime = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mime, true);
			helper.setFrom(fromAddress);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body);
			helper.addAttachment(attachmentPath.getFileName().toString(), attachmentPath.toFile());
			mailSender.send(mime);
		} catch (MessagingException | MailException ex) {
			log.warn("Failed to send email with attachment to {} (subject: {}): {}", to, subject, ex.getMessage());
		}
	}

	/**
	 * Several callers build the subject from user-controlled text (tenant name, a support-report
	 * tenant name, ...) which is validated as non-blank but not restricted in character set — a
	 * newline in there would otherwise land in a raw SMTP header, letting an attacker inject
	 * arbitrary extra headers (CRLF/header injection). Fold it to a single line instead of trying
	 * to validate every caller's input.
	 */
	private String sanitizeSubject(String subject) {
		return subject.replaceAll("[\\r\\n]+", " ");
	}

	private boolean sendViaResend(String to, String subject, String body, List<Map<String, String>> attachments) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("from", fromAddress);
		payload.put("to", List.of(to));
		payload.put("subject", subject);
		payload.put("text", body);
		if (!attachments.isEmpty()) {
			payload.put("attachments", attachments);
		}
		try {
			resendClient.post()
					.uri("/emails")
					.header("Authorization", "Bearer " + resendApiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (RestClientException ex) {
			log.warn("Failed to send email via Resend to {} (subject: {}): {}", to, subject, ex.getMessage());
			return false;
		}
	}
}
