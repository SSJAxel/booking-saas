package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalUpdateRequest(@NotNull UUID branchId, @NotBlank String displayName, String bio,
		@Size(max = 500) String photoUrl, @NotNull Boolean active,
		@DecimalMin("0") @DecimalMax("100") BigDecimal serviceCommissionRate,
		@DecimalMin("0") @DecimalMax("100") BigDecimal productCommissionRate) {
}
