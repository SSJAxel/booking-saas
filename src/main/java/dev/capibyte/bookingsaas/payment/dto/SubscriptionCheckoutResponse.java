package dev.capibyte.bookingsaas.payment.dto;

import java.util.UUID;

public record SubscriptionCheckoutResponse(UUID subscriptionId, String checkoutUrl) {
}
