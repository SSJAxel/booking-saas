package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.RescheduleReason;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * date + startTime (wall-clock), same reasoning as BookAppointmentRequest — converting to an
 * absolute Instant needs the tenant's timezone, which only the server can be trusted to resolve
 * (see AppointmentController#toInstant). Professional and service are deliberately absent: a
 * reschedule only ever moves the same appointment to a new time, for the same client, professional
 * and service — see AppointmentService#reschedule.
 */
public record RescheduleRequest(@NotNull LocalDate date, @NotNull LocalTime startTime,
		@NotNull RescheduleReason reason) {
}
