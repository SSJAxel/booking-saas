package dev.capibyte.bookingsaas.notification;

import java.math.BigDecimal;

/**
 * Fired when a MercadoPago deposit is confirmed PAID for an appointment that
 * PendingDepositExpirationScheduler already cancelled for non-payment — the payment and the
 * appointment's fate diverged while the checkout was in flight. Carries display-ready data (not
 * IDs), same reason as AppointmentNotificationEvent: the listener runs AFTER_COMMIT, once
 * TenantContext is already gone, so it can't re-query anything itself.
 */
public record OrphanedDepositPaymentEvent(
		String ownerEmail,
		String tenantName,
		String clientName,
		String clientEmail,
		String serviceName,
		BigDecimal amount) {
}
