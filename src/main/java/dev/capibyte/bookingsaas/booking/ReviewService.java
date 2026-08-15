package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.PublicReviewResponse;
import dev.capibyte.bookingsaas.booking.dto.ReviewInviteResponse;
import dev.capibyte.bookingsaas.booking.dto.ReviewResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.notification.ReviewInviteEvent;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Its own bean (like {@code AppUserService}/{@code TenantService}) for the token issue/consume
 * lifecycle — same shape as {@code AppUserService.issuePasswordResetToken}/{@code #resetPassword},
 * just scoped to an {@link Appointment} instead of an {@code AppUser}, since there are no client
 * accounts to attach a reset-style token to.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

	// Long enough that a client who just finished a visit has real time to leave a review — unlike
	// AppUserService's 1-hour password-reset window, there's no security reason to keep this short;
	// it's just an invite that eventually goes stale.
	private static final Duration REVIEW_TOKEN_TTL = Duration.ofDays(30);

	private final ReviewRepository reviewRepository;
	private final AppointmentRepository appointmentRepository;
	private final ClientRepository clientRepository;
	private final ProfessionalService professionalService;
	private final ServiceOfferingService serviceOfferingService;
	private final TenantService tenantService;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * No-ops unless the tenant has reviews turned on (see {@code Tenant#reviewsEnabled}'s Javadoc).
	 * {@code professional}/{@code service} are passed in rather than re-fetched here:
	 * {@code AppointmentService.transitionStatus}'s COMPLETED case already loaded them for the
	 * client-facing notification just above this call. Mailing happens via {@link ReviewInviteEvent},
	 * AFTER_COMMIT — same reasoning as {@code AppointmentNotificationEvent}: never invite a client to
	 * review a transition that then rolls back.
	 */
	public void issueInviteIfEnabled(Tenant tenant, Appointment appointment, Client client, Professional professional,
			ServiceOffering service) {
		if (!tenant.isReviewsEnabled()) {
			return;
		}
		String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		appointment.setReviewToken(token);
		appointment.setReviewTokenExpiresAt(Instant.now().plus(REVIEW_TOKEN_TTL));
		eventPublisher.publishEvent(new ReviewInviteEvent(client.getEmail(), client.getName(),
				professional.getDisplayName(), service.getName(), tenant.getSlug(), token));
	}

	@Transactional(readOnly = true)
	public ReviewInviteResponse findInvite(String token) {
		Appointment appointment = findValidInvite(token);
		Client client = clientRepository.findById(appointment.getClientId())
				.orElseThrow(() -> new NotFoundException("Client not found: " + appointment.getClientId()));
		Professional professional = professionalService.findById(appointment.getProfessionalId());
		ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
		return new ReviewInviteResponse(client.getName(), professional.getDisplayName(), service.getName());
	}

	@Transactional
	public void submit(String token, int rating, String comment) {
		Appointment appointment = findValidInvite(token);

		Review review = new Review();
		review.setAppointmentId(appointment.getId());
		review.setClientId(appointment.getClientId());
		review.setProfessionalId(appointment.getProfessionalId());
		review.setRating(rating);
		review.setComment(comment);
		reviewRepository.save(review);

		appointment.setReviewToken(null);
		appointment.setReviewTokenExpiresAt(null);
	}

	/** Empty (not an error) while the tenant has reviews disabled — same "disabled = empty"
	 * convention as {@code ReportService#commissions} — even if reviews already exist on file from
	 * when it was enabled. */
	@Transactional(readOnly = true)
	public List<PublicReviewResponse> publicReviews() {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		if (!tenant.isReviewsEnabled()) {
			return List.of();
		}
		return reviewRepository.findAllByVisibleTrueOrderByCreatedAtDesc().stream().map(this::toPublicResponse)
				.toList();
	}

	/** Owner-facing moderation list — every review, visible or hidden, newest first. */
	@Transactional(readOnly = true)
	public List<ReviewResponse> findAllForOwner() {
		return reviewRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toOwnerResponse).toList();
	}

	@Transactional
	public ReviewResponse setVisibility(UUID id, boolean visible) {
		Review review = reviewRepository.findById(id).orElseThrow(() -> new NotFoundException("Review not found: " + id));
		review.setVisible(visible);
		return toOwnerResponse(review);
	}

	/** Permanent — unlike {@link #setVisibility}, there's no undo. Hiding is the reversible default
	 * for "this review shouldn't be shown"; this is for the owner who wants it gone entirely (e.g.
	 * spam, or a review they don't want sitting in their own moderation list either). Doesn't touch
	 * the appointment's (already-cleared, single-use) review token — deleting the review never
	 * re-opens the invite. */
	@Transactional
	public void delete(UUID id) {
		if (!reviewRepository.existsById(id)) {
			throw new NotFoundException("Review not found: " + id);
		}
		reviewRepository.deleteById(id);
	}

	private Appointment findValidInvite(String token) {
		Appointment appointment = appointmentRepository.findByReviewToken(token)
				.orElseThrow(() -> new BadRequestException("Invalid or already-used review link"));
		if (appointment.getReviewTokenExpiresAt() == null || appointment.getReviewTokenExpiresAt().isBefore(Instant.now())) {
			throw new BadRequestException("This review link expired");
		}
		return appointment;
	}

	private PublicReviewResponse toPublicResponse(Review review) {
		Client client = clientRepository.findById(review.getClientId())
				.orElseThrow(() -> new NotFoundException("Client not found: " + review.getClientId()));
		Professional professional = professionalService.findById(review.getProfessionalId());
		return new PublicReviewResponse(firstName(client.getName()), professional.getDisplayName(),
				review.getRating(), review.getComment(), review.getCreatedAt());
	}

	private ReviewResponse toOwnerResponse(Review review) {
		Client client = clientRepository.findById(review.getClientId())
				.orElseThrow(() -> new NotFoundException("Client not found: " + review.getClientId()));
		Professional professional = professionalService.findById(review.getProfessionalId());
		return new ReviewResponse(review.getId(), client.getName(), professional.getDisplayName(), review.getRating(),
				review.getComment(), review.isVisible(), review.getCreatedAt());
	}

	private String firstName(String fullName) {
		int spaceIndex = fullName.indexOf(' ');
		return spaceIndex == -1 ? fullName : fullName.substring(0, spaceIndex);
	}
}
