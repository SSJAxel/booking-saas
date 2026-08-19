package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.constraints.Size;

/** Null/blank clears each field — same convention as every other freeform field in this app
 * (branding, birthday message). {@code notes} stays general (trato/personalidad);
 * {@code servicePreferences} is the technical "qué le hicimos y cómo"; {@code allergies} is safety
 * data. Deliberately not three separate requests — see ClientService#updateProfile. */
public record ClientProfileUpdateRequest(
		@Size(max = 2000) String notes,
		@Size(max = 2000) String servicePreferences,
		@Size(max = 1000) String allergies) {
}
