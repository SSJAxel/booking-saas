package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfessionalUpdateRequest(@NotBlank String displayName, String bio, @NotNull Boolean active) {
}
