package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BranchHoursRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
		@NotNull LocalTime endTime) {
}
