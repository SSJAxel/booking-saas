package dev.capibyte.bookingsaas.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this app actually reads from MercadoPago's GET /v1/payments/{id} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPayment(String id, String status, @JsonProperty("external_reference") String externalReference) {
}
