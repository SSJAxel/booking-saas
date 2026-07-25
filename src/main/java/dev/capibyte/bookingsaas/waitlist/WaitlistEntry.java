package dev.capibyte.bookingsaas.waitlist;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Date-level, not exact-time: a client waitlists for "this professional/service on this date"
 * rather than one specific slot, since freeing up any appointment that day changes what's
 * available and the client can re-check exact times themselves via the availability endpoint once
 * notified.
 */
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WaitlistEntry extends BaseTenantEntity {

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(nullable = false)
	private LocalDate date;

	@Column(name = "client_name", nullable = false)
	private String clientName;

	@Column(name = "client_email", nullable = false)
	private String clientEmail;

	@Column(name = "client_phone")
	private String clientPhone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WaitlistStatus status = WaitlistStatus.WAITING;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
