package dev.capibyte.bookingsaas.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this app reads from MercadoPago's Preapproval (subscriptions) API responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPreapproval(String id, String status, @JsonProperty("init_point") String initPoint,
		@JsonProperty("external_reference") String externalReference) {
}
