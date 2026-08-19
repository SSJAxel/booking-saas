package dev.capibyte.bookingsaas.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceComboRequest(
		@NotNull UUID serviceAId,
		@NotNull UUID serviceBId,
		@NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal comboPrice,
		@DecimalMin(value = "0", inclusive = true) BigDecimal comboDepositAmount) {
}
