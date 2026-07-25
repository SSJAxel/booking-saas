package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies this client builds requests matching MercadoPago's documented Checkout Pro contract
 * and parses their responses correctly — the closest thing to an integration check available
 * without live sandbox credentials (see README).
 */
class MercadoPagoClientTest {

	private MockRestServiceServer mockServer;
	private MercadoPagoClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new MercadoPagoClient(builder, "https://api.mercadopago.com", "TEST-access-token",
				"https://example.com/success", "https://example.com/failure", "https://example.com/pending");
	}

	@Test
	void createPreferenceSendsTheExpectedRequestAndParsesTheResponse() {
		UUID tenantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();

		mockServer.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer TEST-access-token"))
				.andExpect(content().string(containsString(tenantId + ":" + paymentId)))
				.andExpect(content().string(containsString("Deposit for Small Tattoo")))
				.andRespond(withSuccess(
						"""
						{"id":"pref-123","init_point":"https://mercadopago.com/checkout/pref-123"}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPreference preference = client.createPreference(tenantId, paymentId, "Deposit for Small Tattoo",
				new BigDecimal("50.00"));

		assertThat(preference.id()).isEqualTo("pref-123");
		assertThat(preference.initPoint()).isEqualTo("https://mercadopago.com/checkout/pref-123");
		mockServer.verify();
	}

	@Test
	void getPaymentParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/v1/payments/mp-999"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer TEST-access-token"))
				.andRespond(withSuccess(
						"""
						{"id":"mp-999","status":"approved","external_reference":"tenant-id:payment-id","extra_field_we_ignore":true}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPayment payment = client.getPayment("mp-999");

		assertThat(payment.id()).isEqualTo("mp-999");
		assertThat(payment.status()).isEqualTo("approved");
		assertThat(payment.externalReference()).isEqualTo("tenant-id:payment-id");
		mockServer.verify();
	}
}
