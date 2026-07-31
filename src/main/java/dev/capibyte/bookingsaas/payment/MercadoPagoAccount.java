package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant's own connected MercadoPago account (OAuth Connect), one row per tenant. accessToken
 * is what MercadoPagoAccountService.resolveAccessToken hands to MercadoPagoClient so that
 * checkouts/subscriptions this tenant creates pay out to their own account, not the shared
 * platform one. refreshToken/expiresAt exist so an expired accessToken can be renewed without the
 * owner reconnecting by hand.
 */
@Entity
@Table(name = "mercadopago_accounts")
@Getter
@Setter
@NoArgsConstructor
public class MercadoPagoAccount extends BaseTenantEntity {

	@Column(name = "mp_user_id", nullable = false)
	private String mercadoPagoUserId;

	@Column(name = "access_token", nullable = false)
	private String accessToken;

	@Column(name = "refresh_token", nullable = false)
	private String refreshToken;

	@Column(name = "public_key")
	private String publicKey;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

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
