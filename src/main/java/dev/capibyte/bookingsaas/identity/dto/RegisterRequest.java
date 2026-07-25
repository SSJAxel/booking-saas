package dev.capibyte.bookingsaas.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String tenantName,

		@NotBlank
		@Pattern(regexp = "^[a-z0-9](-?[a-z0-9])*$", message = "must be lowercase alphanumeric with optional single hyphens")
		@Size(min = 3, max = 50)
		String tenantSlug,

		@NotBlank @Email String ownerEmail,

		@NotBlank @Size(min = 8, max = 100) String ownerPassword) {
}
