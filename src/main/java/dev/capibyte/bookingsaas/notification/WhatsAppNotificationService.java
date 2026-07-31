package dev.capibyte.bookingsaas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Same contract as {@link MailService}: a failed send is logged and swallowed, never rethrown —
 * a WhatsApp outage must never fail a booking, since email alone is already enough for one to
 * work.
 */
@Component
public class WhatsAppNotificationService {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

	private final WhatsAppClient whatsAppClient;

	public WhatsAppNotificationService(WhatsAppClient whatsAppClient) {
		this.whatsAppClient = whatsAppClient;
	}

	public void send(String toPhone, String body) {
		try {
			whatsAppClient.sendMessage(toPhone, body);
		} catch (RestClientException ex) {
			log.warn("Failed to send WhatsApp message to {}: {}", toPhone, ex.getMessage());
		}
	}
}
