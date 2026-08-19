package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** Null apaga el recargo — ver Tenant.mercadoPagoFeePercent. */
public record MercadoPagoFeeUpdateRequest(
		@DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "30", inclusive = true) BigDecimal feePercent) {
}
