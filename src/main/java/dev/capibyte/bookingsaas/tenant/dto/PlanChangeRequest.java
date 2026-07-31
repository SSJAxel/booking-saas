package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import jakarta.validation.constraints.NotNull;

public record PlanChangeRequest(@NotNull PlanTier planTier) {
}
