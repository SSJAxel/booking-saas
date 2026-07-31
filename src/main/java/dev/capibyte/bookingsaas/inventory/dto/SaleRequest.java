package dev.capibyte.bookingsaas.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record SaleRequest(@NotNull UUID productId, @Positive int quantity, UUID appointmentId) {
}
