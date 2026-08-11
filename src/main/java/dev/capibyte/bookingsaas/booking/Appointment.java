package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The DB-level {@code no_double_booking} EXCLUDE constraint (see V4 migration) — not application
 * code — is what actually guarantees two overlapping appointments for the same professional can
 * never both be inserted, even under concurrent requests. The one deliberate exception is
 * {@code overtime} (see V25 migration): an owner can explicitly double-book a professional on
 * purpose, and those rows sit entirely outside the constraint. {@code endTime} is computed and
 * stored at booking time so a later edit to a service's duration can't retroactively corrupt
 * history. Cancellation is a status, not a deletion — but old history isn't kept forever: see
 * {@link dev.capibyte.bookingsaas.booking.AppointmentService#purgeHistory} (manual, from Turnos →
 * Lista) and {@code AppointmentRetentionScheduler} (automatic, per tenant, driven by
 * {@link dev.capibyte.bookingsaas.tenant.Tenant#getHistoryRetentionMonths()}) for the two paths
 * that do hard-delete rows here. Both are real DELETEs, and both only ever touch appointments
 * whose startTime is already in the past — never a future one.
 */
@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseTenantEntity {

	@Column(name = "branch_id", nullable = false)
	private UUID branchId;

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "client_id", nullable = false)
	private UUID clientId;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false)
	private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

	@Column
	private String notes;

	@Column(name = "is_overtime", nullable = false)
	private boolean overtime;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
