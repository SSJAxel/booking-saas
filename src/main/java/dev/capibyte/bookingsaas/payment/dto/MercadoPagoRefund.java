package dev.capibyte.bookingsaas.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Only the fields this app actually reads from MercadoPago's POST /v1/payments/{id}/refunds
 * response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoRefund(String id, String status) {
}
