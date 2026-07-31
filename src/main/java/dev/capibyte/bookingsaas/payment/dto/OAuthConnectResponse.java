package dev.capibyte.bookingsaas.payment.dto;

/** Where to redirect the owner's browser so they can authorize this app on their own MercadoPago account. */
public record OAuthConnectResponse(String authorizationUrl) {
}
