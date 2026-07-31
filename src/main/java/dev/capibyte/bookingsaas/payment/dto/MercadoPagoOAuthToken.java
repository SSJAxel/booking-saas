package dev.capibyte.bookingsaas.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this app reads from MercadoPago's POST /oauth/token response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoOAuthToken(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("refresh_token") String refreshToken,
		@JsonProperty("public_key") String publicKey,
		@JsonProperty("user_id") long userId,
		@JsonProperty("expires_in") long expiresInSeconds) {
}
