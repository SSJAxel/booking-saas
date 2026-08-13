package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DepositExpirationUpdateRequest(@NotNull @Min(10) @Max(180) Integer depositExpirationMinutes) {
}
