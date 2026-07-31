package dev.capibyte.bookingsaas.payment;

/** Mirrors MercadoPago's own preapproval status values (see SubscriptionService.mapStatus). */
public enum SubscriptionStatus {
	PENDING,
	AUTHORIZED,
	PAUSED,
	CANCELLED
}
