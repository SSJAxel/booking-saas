package dev.capibyte.bookingsaas.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hit directly by the tenant owner's browser after they authorize this app on MercadoPago's own
 * site — not a client of ours, so no JWT and nothing under /api/tenant/**. Public (see
 * SecurityConfig's PUBLIC_PATHS); the tenant is recovered from {@code state} instead of a
 * session/token (see MercadoPagoAccountService.handleOAuthCallback).
 */
@RestController
@RequiredArgsConstructor
public class MercadoPagoOAuthController {

	private final MercadoPagoAccountService mercadoPagoAccountService;

	@GetMapping("/api/mercadopago/oauth/callback")
	public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
		String returnUrl = mercadoPagoAccountService.handleOAuthCallback(code, state);
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, returnUrl).build();
	}
}
