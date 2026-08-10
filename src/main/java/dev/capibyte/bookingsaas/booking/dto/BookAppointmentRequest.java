package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * date + startTime (both wall-clock, matching what GET .../availability returns) rather than a
 * single Instant — converting to an absolute instant requires knowing the tenant's timezone, which
 * only the server can be trusted to get right (see PublicBookingController.book). A client-supplied
 * Instant would silently assume the client's own local time, or worse, UTC.
 */
public record BookAppointmentRequest(
		@NotNull UUID professionalId,
		@NotNull UUID serviceId,
		@NotNull LocalDate date,
		@NotNull LocalTime startTime,
		@NotBlank String clientName,
		@NotBlank @Email String clientEmail,
		String clientPhone,
		String clientInstagram) {
}
