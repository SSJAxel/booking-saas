package dev.capibyte.bookingsaas.admin.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.TenantStatus;
import java.time.LocalDate;
import java.util.UUID;

/** {@code subscriptionStatus} is null when the tenant never subscribed. {@code daysRemaining} is
 * null when no due date has been set, and can be negative (overdue) — "paid up" is deliberately
 * not collapsed into one boolean here, the frontend renders it from these fields together. */
public record AdminTenantSummaryResponse(
		UUID tenantId,
		String name,
		String slug,
		TenantStatus status,
		PlanTier planTier,
		String subscriptionStatus,
		LocalDate nextPaymentDueAt,
		Long daysRemaining) {
}
