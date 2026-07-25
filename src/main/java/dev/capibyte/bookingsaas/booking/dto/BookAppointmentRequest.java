package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record BookAppointmentRequest(
		@NotNull UUID professionalId,
		@NotNull UUID serviceId,
		@NotNull Instant startTime,
		@NotBlank String clientName,
		@NotBlank @Email String clientEmail,
		String clientPhone) {
}
