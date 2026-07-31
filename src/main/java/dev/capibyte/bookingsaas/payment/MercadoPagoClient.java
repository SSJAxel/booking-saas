package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreapproval;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over MercadoPago's REST API (Checkout Pro) — deliberately plain {@link RestClient}
 * calls rather than the official SDK, to keep the HTTP contract explicit and avoid an extra
 * dependency for what's really two endpoints. Not verified against MercadoPago's real sandbox
 * (no test credentials available while building this) — implemented against their published API
 * docs; treat as needing a live smoke test before relying on it.
 */
@Component
public class MercadoPagoClient {

	private final RestClient restClient;
	private final String accessToken;
	private final String successUrl;
	private final String failureUrl;
	private final String pendingUrl;

	public MercadoPagoClient(RestClient.Builder builder,
			@Value("${app.mercadopago.base-url}") String baseUrl,
			@Value("${app.mercadopago.access-token}") String accessToken,
			@Value("${app.mercadopago.success-url}") String successUrl,
			@Value("${app.mercadopago.failure-url}") String failureUrl,
			@Value("${app.mercadopago.pending-url}") String pendingUrl) {
		this.accessToken = accessToken;
		this.successUrl = successUrl;
		this.failureUrl = failureUrl;
		this.pendingUrl = pendingUrl;
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public MercadoPagoPreference createPreference(UUID tenantId, UUID paymentId, String description, BigDecimal amount) {
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

	public MercadoPagoPayment getPayment(String providerPaymentId) {
		return restClient.get()
				.uri("/v1/payments/{id}", providerPaymentId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(MercadoPagoPayment.class);
	}

	/**
	 * Preapproval = MercadoPago's recurring-billing product, distinct from the one-off Checkout Pro
	 * preference above. {@code init_point} here is where the payer authorizes the recurring charge
	 * (not a one-off payment) — same "never trust the webhook payload, always re-fetch" rule
	 * applies once it fires (see {@code getPreapproval}).
	 */
	public MercadoPagoPreapproval createPreapproval(UUID tenantId, UUID subscriptionId, String reason,
			BigDecimal monthlyAmount, String payerEmail) {
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

	public MercadoPagoPreapproval getPreapproval(String providerSubscriptionId) {
		return restClient.get()
				.uri("/preapproval/{id}", providerSubscriptionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(MercadoPagoPreapproval.class);
	}

	public void cancelPreapproval(String providerSubscriptionId) {
		restClient.put()
				.uri("/preapproval/{id}", providerSubscriptionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("status", "cancelled"))
				.retrieve()
				.toBodilessEntity();
	}
}
