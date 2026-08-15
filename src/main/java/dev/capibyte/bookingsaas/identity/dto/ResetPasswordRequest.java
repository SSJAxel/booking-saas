package dev.capibyte.bookingsaas.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code tenantSlug} is what lets this resolve a tenant before the @TenantId-scoped lookup by token. */
public record ResetPasswordRequest(
		@NotBlank String tenantSlug,
		@NotBlank String token,
		@NotBlank @Size(min = 8, max = 100) String newPassword) {
}
