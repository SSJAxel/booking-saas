package dev.capibyte.bookingsaas.booking.dto;

import java.time.LocalDate;

/** Null clears the birthday — same nullable convention as {@link ClientProfileUpdateRequest}. */
public record UpdateClientBirthdayRequest(LocalDate birthDate) {
}
