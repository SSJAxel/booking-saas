package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clients", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseTenantEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column
	private String phone;

	@Column
	private String notes;

	@Column(name = "instagram_handle")
	private String instagramHandle;

	/** Loyalty score, driven by ClientRatingService — starts at 0, has no upper cap (a very
	 * frequent client can keep climbing past the tenant's "top clients" threshold), floored at 0
	 * on penalties. See ClientRatingService's Javadoc for exactly what moves it. */
	@Column(nullable = false)
	private int rating = 0;

	/** Lifetime count of client-communicated cancellations (transitionStatus(CANCELLED) or
	 * AppointmentService.replace) — deliberately NOT incremented by an auto-expired unpaid deposit
	 * (see AppointmentService.expireForNonPayment), so that path never eats into or counts toward
	 * this client's "first cancellation is free" grace. */
	@Column(name = "cancelled_count", nullable = false)
	private int cancelledCount = 0;

	/** "Cliente fijo": manually kept in the "Mejores clientes" panel regardless of {@code rating} —
	 * see ClientController#setPinned. Every tenant has that one loyal regular whose history
	 * predates this system or doesn't fit neatly into the automatic scoring. */
	@Column(nullable = false)
	private boolean pinned = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
