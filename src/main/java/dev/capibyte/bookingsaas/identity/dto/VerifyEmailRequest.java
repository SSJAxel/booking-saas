package dev.capibyte.bookingsaas.identity.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code tenantSlug} is what lets this resolve a tenant before the @TenantId-scoped lookup by token. */
public record VerifyEmailRequest(@NotBlank String tenantSlug, @NotBlank String token) {
}
