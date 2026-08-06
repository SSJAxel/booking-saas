package dev.capibyte.bookingsaas.notification;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Same failure contract as {@link MailService}: a failed send is logged and swallowed, never
 * rethrown — a WhatsApp outage must never fail a booking, since email alone is already enough for
 * one to work.
 *
 * <p>Also throttles: WhatsApp Business numbers get quality-rated by Meta, and a burst of
 * back-to-back messages to the same client (e.g. book → cancel → rebook within a minute) reads as
 * spam-like behavior that can tank that rating or trigger a temporary send limit. Each phone
 * number gets its own minimum gap between sends — {@code nextAllowedSendAt} reserves the next
 * available slot atomically, so concurrent events for the same client queue up in order instead of
 * racing to send immediately.
 */
@Component
public class WhatsAppNotificationService {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

	private final WhatsAppClient whatsAppClient;
	private final TaskScheduler taskScheduler;
	private final Duration minInterval;
	private final Cache<String, Instant> nextAllowedSendAt = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(10))
			.build();

	public WhatsAppNotificationService(WhatsAppClient whatsAppClient, TaskScheduler taskScheduler,
			@Value("${app.whatsapp.min-interval-seconds}") long minIntervalSeconds) {
		this.whatsAppClient = whatsAppClient;
		this.taskScheduler = taskScheduler;
		this.minInterval = Duration.ofSeconds(minIntervalSeconds);
	}

	public void send(String toPhone, String body) {
		Instant now = Instant.now();
		AtomicReference<Instant> reservedSlot = new AtomicReference<>();
		nextAllowedSendAt.asMap().compute(toPhone, (phone, previousSlot) -> {
			Instant slot = (previousSlot == null || previousSlot.isBefore(now)) ? now : previousSlot.plus(minInterval);
			reservedSlot.set(slot);
			return slot;
		});

		Instant slot = reservedSlot.get();
		if (!slot.isAfter(now)) {
			doSend(toPhone, body);
		} else {
			log.debug("Delaying WhatsApp message to {} by {} to respect the per-recipient send gap", toPhone,
					Duration.between(now, slot));
			taskScheduler.schedule(() -> doSend(toPhone, body), slot);
		}
	}

	private void doSend(String toPhone, String body) {
		try {
			whatsAppClient.sendMessage(toPhone, body);
		} catch (RestClientException ex) {
			log.warn("Failed to send WhatsApp message to {}: {}", toPhone, ex.getMessage());
		}
	}
}
