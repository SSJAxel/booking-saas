package dev.capibyte.bookingsaas.admin.dto;

import java.time.LocalDate;

/** {@code nextPaymentDueAt} may be null — that clears the due date (e.g. back to no vencimiento
 * tracked yet), it's not required to always hold a value. */
public record UpdateNextPaymentDueRequest(LocalDate nextPaymentDueAt) {
}
