package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * amount is a snapshot of price * quantity at sale time (see the V7 migration comment) — never
 * recomputed from the current {@code Product.price}, so a later price change can't rewrite past
 * revenue history.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale extends BaseTenantEntity {

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	/** Null for a standalone/walk-in sale not tied to a booked appointment. */
	@Column(name = "appointment_id")
	private UUID appointmentId;

	/** Who rang this sale up, for product-commission attribution (see
	 * ReportService#commissions) — independent of {@link #appointmentId}: SaleService derives this
	 * from the appointment when one is linked, otherwise it's whatever the register picked (still
	 * nullable — an unattributed walk-in sale, same as before this field existed, is a valid
	 * state). */
	@Column(name = "professional_id")
	private UUID professionalId;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
