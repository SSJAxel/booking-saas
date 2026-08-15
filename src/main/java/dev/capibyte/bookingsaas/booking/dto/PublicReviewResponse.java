package dev.capibyte.bookingsaas.booking.dto;

import java.time.Instant;

/** GET /api/public/{tenantSlug}/reviews — only ever the visible ones (see ReviewService#publicReviews).
 * {@code clientName} is truncated to first name only: unlike the private invite (ReviewInviteResponse),
 * this is shown to every visitor of the public booking page. */
public record PublicReviewResponse(String clientName, String professionalName, int rating, String comment,
		Instant createdAt) {
}
