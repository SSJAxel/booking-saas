package dev.capibyte.bookingsaas.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** The dólar blue rate every plan price is currently indexed against — see
 * {@code PlanPricingScheduler} for the ±115 reindex band. */
public record PricingReferenceResponse(BigDecimal referenceBlueRate, Instant updatedAt) {
}
