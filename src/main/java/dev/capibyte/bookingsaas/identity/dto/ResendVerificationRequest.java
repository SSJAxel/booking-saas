package dev.capibyte.bookingsaas.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(@NotBlank String tenantSlug, @NotBlank @Email String email) {
}
