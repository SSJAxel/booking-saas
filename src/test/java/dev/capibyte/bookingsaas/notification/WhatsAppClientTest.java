package dev.capibyte.bookingsaas.notification;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies this client builds requests matching Twilio's documented Messages API contract — the
 * closest thing to an integration check available without live Twilio credentials (see README).
 */
class WhatsAppClientTest {

	private MockRestServiceServer mockServer;
	private WhatsAppClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new WhatsAppClient(builder, "https://api.twilio.com", "TEST-sid", "TEST-token", "+14155238886");
	}

	@Test
	void sendMessageSendsTheExpectedRequest() {
		String expectedAuth = "Basic "
				+ Base64.getEncoder().encodeToString("TEST-sid:TEST-token".getBytes(StandardCharsets.UTF_8));

		mockServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/TEST-sid/Messages.json"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", expectedAuth))
				.andExpect(content().string(Matchers.containsString("From=whatsapp%3A%2B14155238886")))
				.andExpect(content().string(Matchers.containsString("To=whatsapp%3A%2B5491122334455")))
				.andExpect(content().string(Matchers.containsString("Body=Tu+turno+fue+confirmado")))
				.andRespond(withSuccess("{\"sid\":\"SM123\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

		client.sendMessage("+5491122334455", "Tu turno fue confirmado");

		mockServer.verify();
	}
}
