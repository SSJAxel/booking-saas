package dev.capibyte.bookingsaas.support;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Two things a tenant user submits from their dashboard to the founder, sharing one inbox in the
 * super-admin panel (see PlatformAdminController): a bug report (message + a required screenshot)
 * or a "Mejorar plan" request (message only, see TenantPage). {@link #type} tells them apart. */
@Entity
@Table(name = "support_reports")
@Getter
@Setter
@NoArgsConstructor
public class SupportReport extends BaseTenantEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SupportReportType type = SupportReportType.BUG;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	/** Required for BUG, always null for PLAN_UPGRADE — enforced in SupportReportService, not the
	 * column itself, since the rule is per-type. */
	@Column(name = "image_path")
	private String imagePath;

	@Column(name = "image_content_type")
	private String imageContentType;

	/** Raw id, not a @ManyToOne — matches this codebase's convention for referencing another
	 * tenant-scoped row without pulling in a full JPA association (see Appointment.professionalId). */
	@Column(name = "app_user_id", nullable = false)
	private UUID appUserId;

	@Column(nullable = false)
	private boolean resolved = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
