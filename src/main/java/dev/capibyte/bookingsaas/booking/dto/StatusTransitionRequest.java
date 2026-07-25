package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusTransitionRequest(@NotNull AppointmentStatus status) {
}
