package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.payment.dto.MercadoPagoOAuthToken;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreapproval;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoRefund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin wrapper over MercadoPago's REST API — deliberately plain {@link RestClient} calls rather
 * than the official SDK, to keep the HTTP contract explicit. Not verified against MercadoPago's
 * real sandbox (no test credentials available while building this) — implemented against their
 * published API docs; treat as needing a live smoke test before relying on it.
 *
 * <p>Every call that moves money or reads a payment/subscription takes an explicit
 * {@code accessToken} rather than using one baked in at construction — this client has no opinion
 * on whose MercadoPago account a given call is for. {@link MercadoPagoAccountService} decides
 * that: a connected tenant's own token so their money lands in their own account (OAuth Connect),
 * or the platform's shared token as a fallback for a tenant who hasn't connected yet. Only
 * {@code clientId}/{@code clientSecret} (the platform's own OAuth application credentials, used
 * for the token exchange itself) are fixed for the whole app.
 */
@Component
public class MercadoPagoClient {

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;
	private final String successUrl;
	private final String failureUrl;
	private final String pendingUrl;

	public MercadoPagoClient(RestClient.Builder builder,
			@Value("${app.mercadopago.base-url}") String baseUrl,
			@Value("${app.mercadopago.client-id}") String clientId,
			@Value("${app.mercadopago.client-secret}") String clientSecret,
			@Value("${app.mercadopago.success-url}") String successUrl,
			@Value("${app.mercadopago.failure-url}") String failureUrl,
			@Value("${app.mercadopago.pending-url}") String pendingUrl) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.successUrl = successUrl;
		this.failureUrl = failureUrl;
		this.pendingUrl = pendingUrl;
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public MercadoPagoPreference createPreference(String accessToken, UUID tenantId, UUID paymentId,
			String description, BigDecimal amount) {
		// tenantId embedded here (not just paymentId) so the webhook can resolve TenantContext
		// before it needs to look up the tenant-scoped Payment row — see PaymentService.
		String externalReference = tenantId + ":" + paymentId;

		Map<String, Object> item = Map.of(
				"title", description,
				"quantity", 1,
				"unit_price", amount,
				"currency_id", "ARS");
		Map<String, Object> body = Map.of(
				"items", List.of(item),
				"external_reference", externalReference,
				"back_urls", Map.of("success", successUrl, "failure", failureUrl, "pending", pendingUrl),
				"auto_return", "approved");

		return restClient.post()
				.uri("/checkout/preferences")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(MercadoPagoPreference.class);
	}

	public MercadoPagoPayment getPayment(String accessToken, String providerPaymentId) {
		return restClient.get()
				.uri("/v1/payments/{id}", providerPaymentId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(MercadoPagoPayment.class);
	}

	/** Full refund — no request body means "refund the whole amount" per MercadoPago's API. Partial
	 * refunds (a JSON body with "amount") aren't needed by anything in this codebase yet. */
	public MercadoPagoRefund refundPayment(String accessToken, String providerPaymentId) {
		return restClient.post()
				.uri("/v1/payments/{id}/refunds", providerPaymentId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(MercadoPagoRefund.class);
	}

	/**
	 * Preapproval = MercadoPago's recurring-billing product, distinct from the one-off Checkout Pro
	 * preference above. {@code init_point} here is where the payer authorizes the recurring charge
	 * (not a one-off payment) — same "never trust the webhook payload, always re-fetch" rule
	 * applies once it fires (see {@code getPreapproval}).
	 */
	public MercadoPagoPreapproval createPreapproval(String accessToken, UUID tenantId, UUID subscriptionId,
			String reason, BigDecimal monthlyAmount, String payerEmail) {
		String externalReference = tenantId + ":" + subscriptionId;

		Map<String, Object> autoRecurring = Map.of(
				"frequency", 1,
				"frequency_type", "months",
				"transaction_amount", monthlyAmount,
				"currency_id", "ARS");
		Map<String, Object> body = Map.of(
				"reason", reason,
				"external_reference", externalReference,
				"payer_email", payerEmail,
				"back_url", successUrl,
				"auto_recurring", autoRecurring,
				"status", "pending");

		return restClient.post()
				.uri("/preapproval")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(MercadoPagoPreapproval.class);
	}

	public MercadoPagoPreapproval getPreapproval(String accessToken, String providerSubscriptionId) {
		return restClient.get()
				.uri("/preapproval/{id}", providerSubscriptionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(MercadoPagoPreapproval.class);
	}

	public void cancelPreapproval(String accessToken, String providerSubscriptionId) {
		restClient.put()
				.uri("/preapproval/{id}", providerSubscriptionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("status", "cancelled"))
				.retrieve()
				.toBodilessEntity();
	}

	/**
	 * The URL to send a tenant owner's browser to so they can log into their own MercadoPago
	 * account and authorize this app (OAuth Connect, "Authorization Code" flow). {@code state} is
	 * round-tripped back verbatim on the callback — used here to carry the tenant id, the same
	 * "embed the id we'll need later" convention as external_reference elsewhere in this file
	 * (see MercadoPagoAccountService for where it's parsed back out). A hardened version would use
	 * an opaque per-request nonce instead and look the tenant up server-side, to rule out a forged
	 * state value driving the callback to the wrong tenant.
	 */
	public String buildAuthorizationUrl(String state, String redirectUri) {
		return UriComponentsBuilder.fromUriString("https://auth.mercadopago.com/authorization")
				.queryParam("client_id", clientId)
				.queryParam("response_type", "code")
				.queryParam("platform_id", "mp")
				.queryParam("state", state)
				.queryParam("redirect_uri", redirectUri)
				.toUriString();
	}

	public MercadoPagoOAuthToken exchangeCodeForToken(String code, String redirectUri) {
		Map<String, Object> body = Map.of(
				"client_id", clientId,
				"client_secret", clientSecret,
				"grant_type", "authorization_code",
				"code", code,
				"redirect_uri", redirectUri);
		return restClient.post()
				.uri("/oauth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(MercadoPagoOAuthToken.class);
	}

	public MercadoPagoOAuthToken refreshAccessToken(String refreshToken) {
		Map<String, Object> body = Map.of(
				"client_id", clientId,
				"client_secret", clientSecret,
				"grant_type", "refresh_token",
				"refresh_token", refreshToken);
		return restClient.post()
				.uri("/oauth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(MercadoPagoOAuthToken.class);
	}
}
