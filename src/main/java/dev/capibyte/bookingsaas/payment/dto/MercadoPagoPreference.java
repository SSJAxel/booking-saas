package dev.capibyte.bookingsaas.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this app actually reads from MercadoPago's POST /checkout/preferences response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPreference(String id, @JsonProperty("init_point") String initPoint) {
}
