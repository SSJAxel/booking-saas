package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.payment.dto.CheckoutResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;
	private final SubscriptionService subscriptionService;

	/** Public — same tenant resolution as the rest of /api/public/{tenantSlug}/**. */
	@PostMapping("/api/public/{tenantSlug}/appointments/{appointmentId}/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutResponse checkout(@PathVariable String tenantSlug, @PathVariable UUID appointmentId) {
		return paymentService.createCheckout(appointmentId);
	}

	/**
	 * Called by MercadoPago's servers, not a client of ours — no JWT, no tenant slug in the URL.
	 * The tenant is resolved from the fetched payment/subscription's external_reference inside
	 * whichever service handles it. One shared notification URL for every MercadoPago product
	 * (both Checkout Pro deposits and Preapproval subscriptions land here), routed by {@code type} —
	 * defaulted to "payment" since that's the only topic this endpoint handled before subscriptions
	 * existed, and MercadoPago's older payment notifications don't always include it.
	 */
	@PostMapping("/api/webhooks/mercadopago")
	@ResponseStatus(HttpStatus.OK)
	public void mercadoPagoWebhook(
			@RequestParam(value = "type", required = false, defaultValue = "payment") String type,
			@RequestParam("data.id") String dataId,
			@RequestHeader("x-request-id") String requestId,
			@RequestHeader("x-signature") String signature) {
		if ("subscription_preapproval".equals(type)) {
			subscriptionService.handleWebhook(dataId, requestId, signature);
		} else {
			paymentService.handleWebhook(dataId, requestId, signature);
		}
	}
}
