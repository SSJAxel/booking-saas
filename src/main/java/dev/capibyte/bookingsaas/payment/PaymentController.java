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

	/** Public — same tenant resolution as the rest of /api/public/{tenantSlug}/**. */
	@PostMapping("/api/public/{tenantSlug}/appointments/{appointmentId}/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutResponse checkout(@PathVariable String tenantSlug, @PathVariable UUID appointmentId) {
		return paymentService.createCheckout(appointmentId);
	}

	/**
	 * Called by MercadoPago's servers, not a client of ours — no JWT, no tenant slug in the URL.
	 * The tenant is resolved from the fetched payment's external_reference inside the service.
	 */
	@PostMapping("/api/webhooks/mercadopago")
	@ResponseStatus(HttpStatus.OK)
	public void mercadoPagoWebhook(
			@RequestParam("data.id") String dataId,
			@RequestHeader("x-request-id") String requestId,
			@RequestHeader("x-signature") String signature) {
		paymentService.handleWebhook(dataId, requestId, signature);
	}
}
