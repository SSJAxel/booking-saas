package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.ReviewResponse;
import dev.capibyte.bookingsaas.booking.dto.ReviewVisibilityRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Owner/admin moderation of submitted reviews — no STAFF access, same reasoning as
 * {@link dev.capibyte.bookingsaas.staff.ProfessionalController}: hiding a review is a business
 * judgment call. */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ReviewController {

	private final ReviewService reviewService;

	@GetMapping
	public List<ReviewResponse> list() {
		return reviewService.findAllForOwner();
	}

	@PatchMapping("/{id}/visibility")
	public ReviewResponse setVisibility(@PathVariable UUID id, @Valid @RequestBody ReviewVisibilityRequest request) {
		return reviewService.setVisibility(id, request.visible());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		reviewService.delete(id);
	}
}
