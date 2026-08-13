package dev.capibyte.bookingsaas.admin.dto;

import java.math.BigDecimal;

/** {@code customMonthlyPrice} may be null — that clears the override, falling back to the plan
 * tier's list price (see Tenant#getEffectiveMonthlyPrice). */
public record UpdateTenantCustomPriceRequest(BigDecimal customMonthlyPrice) {
}
