package dev.capibyte.bookingsaas.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServiceOfferingUpdateRequest(@NotBlank String name, String description,
		@Size(max = 100) String category, @Positive int durationMinutes,
		@NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
		@DecimalMin(value = "0", inclusive = false) BigDecimal depositAmount, @NotNull Boolean active,
		Boolean featured) {
}
