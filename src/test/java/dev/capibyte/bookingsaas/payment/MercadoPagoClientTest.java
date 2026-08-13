package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.capibyte.bookingsaas.payment.dto.MercadoPagoOAuthToken;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreapproval;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoRefund;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies this client builds requests matching MercadoPago's documented API contracts (Checkout
 * Pro, Preapproval subscriptions, OAuth Connect) and parses their responses correctly — the
 * closest thing to an integration check available without live sandbox credentials (see README).
 */
class MercadoPagoClientTest {

	private MockRestServiceServer mockServer;
	private MercadoPagoClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new MercadoPagoClient(builder, "https://api.mercadopago.com", "TEST-client-id", "TEST-client-secret",
				"https://example.com/success", "https://example.com/failure", "https://example.com/pending");
	}

	@Test
	void createPreferenceSendsTheExpectedRequestAndParsesTheResponse() {
		UUID tenantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();

		mockServer.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer tenant-access-token"))
				.andExpect(content().string(containsString(tenantId + ":" + paymentId)))
				.andExpect(content().string(containsString("Deposit for Small Tattoo")))
				.andRespond(withSuccess(
						"""
						{"id":"pref-123","init_point":"https://mercadopago.com/checkout/pref-123"}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPreference preference = client.createPreference("tenant-access-token", tenantId, paymentId,
				"Deposit for Small Tattoo", new BigDecimal("50.00"));

		assertThat(preference.id()).isEqualTo("pref-123");
		assertThat(preference.initPoint()).isEqualTo("https://mercadopago.com/checkout/pref-123");
		mockServer.verify();
	}

	@Test
	void getPaymentParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/v1/payments/mp-999"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer platform-access-token"))
				.andRespond(withSuccess(
						"""
						{"id":"mp-999","status":"approved","external_reference":"tenant-id:payment-id","extra_field_we_ignore":true}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPayment payment = client.getPayment("platform-access-token", "mp-999");

		assertThat(payment.id()).isEqualTo("mp-999");
		assertThat(payment.status()).isEqualTo("approved");
		assertThat(payment.externalReference()).isEqualTo("tenant-id:payment-id");
		mockServer.verify();
	}

	@Test
	void refundPaymentSendsTheExpectedRequestAndParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/v1/payments/mp-999/refunds"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer tenant-access-token"))
				.andRespond(withSuccess(
						"""
						{"id":"refund-1","status":"approved","payment_id":"mp-999","amount":50.0}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoRefund refund = client.refundPayment("tenant-access-token", "mp-999");

		assertThat(refund.id()).isEqualTo("refund-1");
		assertThat(refund.status()).isEqualTo("approved");
		mockServer.verify();
	}

	@Test
	void createPreapprovalSendsTheExpectedRequestAndParsesTheResponse() {
		UUID tenantId = UUID.randomUUID();
		UUID subscriptionId = UUID.randomUUID();

		mockServer.expect(requestTo("https://api.mercadopago.com/preapproval"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer tenant-access-token"))
				.andExpect(content().string(containsString(tenantId + ":" + subscriptionId)))
				.andExpect(content().string(containsString("owner@example.com")))
				.andExpect(content().string(containsString("\"frequency_type\":\"months\"")))
				.andRespond(withSuccess(
						"""
						{"id":"preapproval-123","status":"pending","init_point":"https://mercadopago.com/subscriptions/preapproval-123","external_reference":"ignored-here"}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPreapproval preapproval = client.createPreapproval("tenant-access-token", tenantId, subscriptionId,
				"PRO plan", new BigDecimal("15000.00"), "owner@example.com");

		assertThat(preapproval.id()).isEqualTo("preapproval-123");
		assertThat(preapproval.status()).isEqualTo("pending");
		assertThat(preapproval.initPoint()).isEqualTo("https://mercadopago.com/subscriptions/preapproval-123");
		mockServer.verify();
	}

	@Test
	void getPreapprovalParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/preapproval/preapproval-999"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer platform-access-token"))
				.andRespond(withSuccess(
						"""
						{"id":"preapproval-999","status":"authorized","external_reference":"tenant-id:subscription-id"}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoPreapproval preapproval = client.getPreapproval("platform-access-token", "preapproval-999");

		assertThat(preapproval.id()).isEqualTo("preapproval-999");
		assertThat(preapproval.status()).isEqualTo("authorized");
		assertThat(preapproval.externalReference()).isEqualTo("tenant-id:subscription-id");
		mockServer.verify();
	}

	@Test
	void cancelPreapprovalSendsTheExpectedRequest() {
		mockServer.expect(requestTo("https://api.mercadopago.com/preapproval/preapproval-999"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header("Authorization", "Bearer tenant-access-token"))
				.andExpect(content().string(containsString("\"status\":\"cancelled\"")))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.cancelPreapproval("tenant-access-token", "preapproval-999");

		mockServer.verify();
	}

	@Test
	void buildAuthorizationUrlIncludesTheClientIdStateAndRedirectUri() {
		String url = client.buildAuthorizationUrl("tenant-id-as-state", "https://example.com/oauth/callback");

		assertThat(url).startsWith("https://auth.mercadopago.com/authorization");
		assertThat(url).contains("client_id=TEST-client-id");
		assertThat(url).contains("state=tenant-id-as-state");
		assertThat(url).contains("redirect_uri=https://example.com/oauth/callback");
	}

	@Test
	void exchangeCodeForTokenSendsTheExpectedRequestAndParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/oauth/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(containsString("\"grant_type\":\"authorization_code\"")))
				.andExpect(content().string(containsString("TEST-client-secret")))
				.andExpect(content().string(containsString("auth-code-123")))
				.andRespond(withSuccess(
						"""
						{"access_token":"seller-token","refresh_token":"seller-refresh","public_key":"pub-key","user_id":555,"expires_in":15552000}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoOAuthToken token = client.exchangeCodeForToken("auth-code-123", "https://example.com/oauth/callback");

		assertThat(token.accessToken()).isEqualTo("seller-token");
		assertThat(token.refreshToken()).isEqualTo("seller-refresh");
		assertThat(token.userId()).isEqualTo(555L);
		assertThat(token.expiresInSeconds()).isEqualTo(15552000L);
		mockServer.verify();
	}

	@Test
	void refreshAccessTokenSendsTheExpectedRequestAndParsesTheResponse() {
		mockServer.expect(requestTo("https://api.mercadopago.com/oauth/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(containsString("\"grant_type\":\"refresh_token\"")))
				.andExpect(content().string(containsString("old-refresh-token")))
				.andRespond(withSuccess(
						"""
						{"access_token":"new-token","refresh_token":"new-refresh","user_id":555,"expires_in":15552000}
						""",
						MediaType.APPLICATION_JSON));

		MercadoPagoOAuthToken token = client.refreshAccessToken("old-refresh-token");

		assertThat(token.accessToken()).isEqualTo("new-token");
		assertThat(token.refreshToken()).isEqualTo("new-refresh");
		mockServer.verify();
	}
}
