package dev.capibyte.bookingsaas.booking;

/**
 * Why an appointment is being moved to a new date/time (see AppointmentService#reschedule) — drives
 * both whether the client's rating takes a hit and whether an already-paid deposit survives the
 * move:
 * <ul>
 *   <li>{@link #TENANT_DECISION}: the business decided to move the slot (e.g. a professional called
 *   in sick). Never touches {@link Client#getRating()} or {@link Client#getRescheduledCount()}, and
 *   an already-PAID deposit is always kept, no matter how many times this happens.</li>
 *   <li>{@link #CLIENT_NOTICE}: the client asked for the change. Same grace shape as a cancellation
 *   — free the first time, -2 points from the second time on (see
 *   ClientRatingService#recordRescheduledByClient) — and an already-PAID deposit is only kept the
 *   first time; from the second reschedule under this reason on, the deposit is forfeited.</li>
 * </ul>
 * Shown to the owner in Spanish as "Personal del tenant" / "Aviso" — see RescheduleModal on the
 * frontend.
 */
public enum RescheduleReason {
	TENANT_DECISION,
	CLIENT_NOTICE
}
