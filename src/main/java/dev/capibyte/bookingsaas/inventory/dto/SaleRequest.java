package dev.capibyte.bookingsaas.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** {@code professionalId} attributes a walk-in sale (no {@code appointmentId}) for commission
 * purposes — ignored server-side when {@code appointmentId} is set, since that sale's professional
 * is derived from the appointment instead (see SaleService#recordSale). */
public record SaleRequest(@NotNull UUID productId, @Positive int quantity, UUID appointmentId, UUID professionalId) {
}
