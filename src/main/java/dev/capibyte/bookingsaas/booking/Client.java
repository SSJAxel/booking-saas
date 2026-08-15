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

	/** Lifetime count of client-communicated cancellations (transitionStatus(CANCELLED)) —
	 * deliberately NOT incremented by an auto-expired unpaid deposit (see
	 * AppointmentService.expireForNonPayment), so that path never eats into or counts toward this
	 * client's "first cancellation is free" grace. Also NOT incremented by a "Reagendar" with motivo
	 * Aviso — that has its own separate grace, see {@link #rescheduledCount}. */
	@Column(name = "cancelled_count", nullable = false)
	private int cancelledCount = 0;

	/** Lifetime count of "Reagendar" actions with motivo "Aviso" (the client asked for the change) —
	 * deliberately separate from {@link #cancelledCount}: rescheduling isn't cancelling, so it gets
	 * its own "first time is free" grace, tracked and spent independently. Never incremented for
	 * motivo "Personal del tenant" — the business moving the appointment on its own initiative never
	 * touches rating or this counter. See ClientRatingService#recordRescheduledByClient and
	 * AppointmentService#reschedule. */
	@Column(name = "rescheduled_count", nullable = false)
	private int rescheduledCount = 0;

	/** Loyalty points balance — +1 per COMPLETED appointment (LoyaltyPointsService, only while the
	 * tenant has loyalty rewards enabled), clamped at Tenant#loyaltyPointsCap, spent (not reset) on
	 * redemption (ClientService#redeemReward). Deliberately separate from {@link #rating}: rating is
	 * a non-spendable reliability score, this is a spendable currency — conflating them would make
	 * neither make sense. */
	@Column(name = "loyalty_points", nullable = false)
	private int loyaltyPoints = 0;

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
