package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProfessionalRequest(@NotNull UUID branchId, @NotBlank String displayName, String bio) {
}
