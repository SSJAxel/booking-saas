package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.PublicReviewResponse;
import dev.capibyte.bookingsaas.booking.dto.ReviewInviteResponse;
import dev.capibyte.bookingsaas.booking.dto.SubmitReviewRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No auth here — {@link PublicTenantResolutionFilter} resolves the tenant from {tenantSlug}
 * before this runs, same reasoning as {@link PublicBookingController}. Rate-limited the same way
 * every other {@code /api/public/**} route is, by prefix (see PublicApiRateLimitFilter) — nothing
 * extra needed here.
 */
@RestController
@RequestMapping("/api/public/{tenantSlug}/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

	private final ReviewService reviewService;

	@GetMapping("/invite/{token}")
	public ReviewInviteResponse invite(@PathVariable String tenantSlug, @PathVariable String token) {
		return reviewService.findInvite(token);
	}

	@PostMapping("/invite/{token}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void submit(@PathVariable String tenantSlug, @PathVariable String token,
			@Valid @RequestBody SubmitReviewRequest request) {
		reviewService.submit(token, request.rating(), request.comment());
	}

	@GetMapping
	public List<PublicReviewResponse> list(@PathVariable String tenantSlug) {
		return reviewService.publicReviews();
	}
}
