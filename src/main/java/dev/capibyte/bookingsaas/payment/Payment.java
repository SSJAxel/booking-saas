package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.booking.PaymentStatus;
import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per deposit checkout attempt. Created (status PENDING, providerPaymentId still null)
 * when the checkout preference is requested; updated by the webhook once MercadoPago confirms the
 * actual payment outcome — never trust the webhook payload's status field directly, always
 * re-fetch from MercadoPago's API server-to-server (see PaymentService).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseTenantEntity {

	@Column(name = "appointment_id", nullable = false)
	private UUID appointmentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentProvider provider = PaymentProvider.MERCADO_PAGO;

	@Column(name = "provider_preference_id")
	private String providerPreferenceId;

	@Column(name = "provider_payment_id")
	private String providerPaymentId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private String currency = "ARS";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status = PaymentStatus.PENDING;

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
