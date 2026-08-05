package dev.capibyte.bookingsaas.admin.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantPlanRequest(@NotNull PlanTier planTier) {
}
