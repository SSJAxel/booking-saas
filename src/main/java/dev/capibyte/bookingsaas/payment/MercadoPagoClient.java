package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
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
}
