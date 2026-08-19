package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant-defined discount for booking two specific services together (e.g. tattoo + piercing at
 * $105 instead of $90+$30=$120) — MAX plan only, see {@link dev.capibyte.bookingsaas.tenant.PlanTier
 * #isServiceCombosEnabled()}. {@code serviceAId}/{@code serviceBId} are always stored in canonical
 * order (smaller {@link UUID} first, see {@code ServiceComboService#canonicalPair}) so a lookup
 * never has to try both orderings and the DB unique index actually catches duplicates.
 *
 * <p>Purely a scheduling-time price/deposit override, applied only when a client's booking group is
 * exactly these two services (see {@code AppointmentService#bookGroup}) — never confused with the
 * booking-group feature itself ({@code Appointment#bookingGroupId}), which works with or without a
 * combo existing for the pair.
 */
@Entity
@Table(name = "service_combos")
@Getter
@Setter
@NoArgsConstructor
public class ServiceCombo extends BaseTenantEntity {

	@Column(name = "service_a_id", nullable = false)
	private UUID serviceAId;

	@Column(name = "service_b_id", nullable = false)
	private UUID serviceBId;

	/** Combined display price for both services together — never charged directly (this app only
	 * ever collects the deposit via Mercado Pago, the rest is paid in person), purely what the
	 * client sees instead of the sum of each service's own {@code price}. */
	@Column(name = "combo_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal comboPrice;

	/** Null means no override — each leg keeps its own {@code ServiceOffering.depositAmount}, only
	 * {@link #comboPrice} changes. When set, the entire amount is charged on the first leg of the
	 * booking group and the second leg becomes {@code PaymentStatus.NOT_REQUIRED} — see
	 * {@code AppointmentService#bookGroup}. */
	@Column(name = "combo_deposit_amount", precision = 10, scale = 2)
	private BigDecimal comboDepositAmount;

	@Column(nullable = false)
	private boolean active = true;
}
