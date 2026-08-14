package dev.capibyte.bookingsaas.payment.dto;

/** Whether this tenant has its own MercadoPago account connected (OAuth Connect) — {@code false}
 * just means checkouts/subscriptions fall back to the shared platform account, not that anything
 * is broken. */
public record MercadoPagoConnectionStatusResponse(boolean connected) {
}
