package dev.capibyte.bookingsaas.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The real, current ARS list price for a paid {@link PlanTier} — moved out of the enum (see its
 * Javadoc) so {@code PlanPricingScheduler} can index it to the dólar blue at runtime, without a
 * redeploy. Not tenant-scoped (extends nothing tenant-related, no {@code @TenantId}) — this is
 * platform-wide pricing, the same for every tenant on a given tier.
 *
 * <p>{@code usdEquivalent} is the locked USD-equivalent target computed when {@code arsPrice} was
 * last set (originally {@code arsPrice / referenceBlueRate} at feature-adoption time) —
 * reindexing multiplies this by the new blue rate to get the new {@code arsPrice}, keeping the
 * implicit USD price stable across pesos-only price changes.
 */
@Entity
@Table(name = "plan_pricing")
@Getter
@Setter
@NoArgsConstructor
public class PlanPricing {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "plan_tier")
	private PlanTier planTier;

	@Column(name = "ars_price", nullable = false)
	private BigDecimal arsPrice;

	@Column(name = "usd_equivalent", nullable = false)
	private BigDecimal usdEquivalent;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
