package dev.capibyte.bookingsaas.payment.dto;

import java.util.UUID;

public record CheckoutResponse(UUID paymentId, String checkoutUrl) {
}
