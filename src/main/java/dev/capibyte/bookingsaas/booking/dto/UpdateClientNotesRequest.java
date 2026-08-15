package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.constraints.Size;

/** Null/blank clears the note — an empty comment is a valid state, same as clearing branding. */
public record UpdateClientNotesRequest(@Size(max = 2000) String notes) {
}
