package dev.capibyte.bookingsaas.booking;

/**
 * Shared by {@code Appointment.paymentStatus} (a simplified summary) and {@code Payment.status}
 * (an individual payment transaction) — a Payment row just never uses NOT_REQUIRED, since one
 * only ever gets created once a deposit checkout has actually started.
 */
public enum PaymentStatus {
	NOT_REQUIRED,
	PENDING,
	PAID,
	FAILED,
	REFUNDED
}
