package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per subscription attempt, not one per tenant — a tenant can cancel and resubscribe
 * later. "The current one" is whichever row is AUTHORIZED/PENDING most recently (see
 * SubscriptionRepository). Status is only ever updated from a MercadoPago webhook
 * (SubscriptionService.handleWebhook) or an explicit cancellation — never optimistically.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseTenantEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_tier", nullable = false)
	private PlanTier planTier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentProvider provider = PaymentProvider.MERCADO_PAGO;

	@Column(name = "provider_subscription_id")
	private String providerSubscriptionId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private String currency = "ARS";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubscriptionStatus status = SubscriptionStatus.PENDING;

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
