package dev.capibyte.bookingsaas.staff.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record TimeOffRequest(@NotNull LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {
}
