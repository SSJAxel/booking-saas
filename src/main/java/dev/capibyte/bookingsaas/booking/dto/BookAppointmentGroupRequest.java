package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Two or more services booked together in one flow ("corte con Lauti" + "tratamiento capilar con
 * Facu", el mismo día o en días distintos) — one client identity shared across every item, same
 * date/startTime-not-Instant reasoning as {@link BookAppointmentRequest}.
 */
public record BookAppointmentGroupRequest(
		@NotEmpty @Valid List<Item> items,
		@NotBlank String clientName,
		@NotBlank @Email String clientEmail,
		String clientPhone,
		String clientInstagram) {

	public record Item(
			@NotNull UUID professionalId,
			@NotNull UUID serviceId,
			@NotNull LocalDate date,
			@NotNull LocalTime startTime) {
	}
}
