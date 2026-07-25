package dev.capibyte.bookingsaas.waitlist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record JoinWaitlistRequest(
		@NotNull UUID professionalId,
		@NotNull UUID serviceId,
		@NotNull @FutureOrPresent LocalDate date,
		@NotBlank String clientName,
		@NotBlank @Email String clientEmail,
		String clientPhone) {
}
