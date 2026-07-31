package dev.capibyte.bookingsaas.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Thin wrapper over Twilio's WhatsApp messaging API — plain {@link RestClient}, same style as
 * {@link dev.capibyte.bookingsaas.payment.MercadoPagoClient}, not the official SDK. One
 * platform-level Twilio sender for every tenant (mirrors the MercadoPago "one platform account"
 * MVP simplification) — a tenant only controls whether messages go out via
 * {@code Tenant.whatsappEnabled}, not which number they're sent from. Not verified against a live
 * Twilio account (no test credentials available while building this); implemented against their
 * published API docs — treat as needing a live smoke test before relying on it.
 */
@Component
public class WhatsAppClient {

	private final RestClient restClient;
	private final String accountSid;
	private final String whatsappFrom;
	private final String authHeader;

	public WhatsAppClient(RestClient.Builder builder,
			@Value("${app.twilio.base-url}") String baseUrl,
			@Value("${app.twilio.account-sid}") String accountSid,
			@Value("${app.twilio.auth-token}") String authToken,
			@Value("${app.twilio.whatsapp-from}") String whatsappFrom) {
		this.accountSid = accountSid;
		this.whatsappFrom = whatsappFrom;
		this.authHeader = "Basic "
				+ Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public void sendMessage(String toPhone, String body) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("From", "whatsapp:" + whatsappFrom);
		form.add("To", "whatsapp:" + toPhone);
		form.add("Body", body);

		restClient.post()
				.uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
				.header(HttpHeaders.AUTHORIZATION, authHeader)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
	}
}
