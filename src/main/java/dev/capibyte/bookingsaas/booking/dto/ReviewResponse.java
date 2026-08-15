package dev.capibyte.bookingsaas.booking.dto;

import java.time.Instant;
import java.util.UUID;

/** Owner-facing moderation view (GET/PATCH /api/reviews) — unlike PublicReviewResponse this
 * carries the review's own id (needed to toggle it) and visible (hidden ones are shown here too,
 * unlike the public list). Full client name, since only OWNER/ADMIN sees this. */
public record ReviewResponse(UUID id, String clientName, String professionalName, int rating, String comment,
		boolean visible, Instant createdAt) {
}
