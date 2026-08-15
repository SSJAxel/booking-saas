package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client's post-visit star rating + optional comment, submitted through the single-use invite
 * token on {@link Appointment#getReviewToken()} — see {@code ReviewService}. {@code visible}
 * defaults true (public, post-moderation: it goes live immediately, and the owner can hide it
 * afterward via {@code ReviewController#setVisibility}, never a pre-approval queue).
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = "appointment_id"))
@Getter
@Setter
@NoArgsConstructor
public class Review extends BaseTenantEntity {

	@Column(name = "appointment_id", nullable = false)
	private UUID appointmentId;

	@Column(name = "client_id", nullable = false)
	private UUID clientId;

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(nullable = false)
	private int rating;

	@Column
	private String comment;

	@Column(nullable = false)
	private boolean visible = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
