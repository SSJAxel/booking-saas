package dev.capibyte.bookingsaas.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ServiceOfferingRequest(@NotBlank String name, String description, @Positive int durationMinutes,
		@NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
		@DecimalMin(value = "0", inclusive = false) BigDecimal depositAmount) {
}
