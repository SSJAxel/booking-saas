package dev.capibyte.bookingsaas.support;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A bug report a tenant user submits from their dashboard — message + an uploaded image,
 * reviewed by the founder in the super-admin panel (see PlatformAdminController). */
@Entity
@Table(name = "support_reports")
@Getter
@Setter
@NoArgsConstructor
public class SupportReport extends BaseTenantEntity {

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Column(name = "image_path", nullable = false)
	private String imagePath;

	@Column(name = "image_content_type", nullable = false)
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
