package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends BaseTenantEntity {

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	/** Administrative on/off switch — separate from {@link #emailVerified}, which gates login. */
	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	@Column(name = "verification_token")
	private String verificationToken;

	@Column(name = "verification_token_expires_at")
	private Instant verificationTokenExpiresAt;

	/** Personal profile, shown in "Mi cuenta" — separate from the tenant's own branding. Both
	 * optional; an unset displayName falls back to showing the email in the UI. */
	@Column(name = "display_name")
	private String displayName;

	@Column(name = "avatar_url")
	private String avatarUrl;
}
