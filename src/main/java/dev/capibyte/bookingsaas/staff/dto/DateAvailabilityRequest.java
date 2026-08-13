package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record DateAvailabilityRequest(@NotNull LocalDate date, @NotNull LocalTime startTime,
		@NotNull LocalTime endTime) {
}
