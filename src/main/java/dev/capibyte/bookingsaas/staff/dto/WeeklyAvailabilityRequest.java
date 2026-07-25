package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record WeeklyAvailabilityRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
		@NotNull LocalTime endTime) {
}
