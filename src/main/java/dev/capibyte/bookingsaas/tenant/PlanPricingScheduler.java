package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reindexes every priced plan's ARS list price to the dólar blue, but only when it actually needs
 * to: the real rate has to drift away from {@code platform_settings.reference_blue_rate} by at
 * least {@value #BAND_DOWN_ARS} pesos on the way DOWN or {@value #BAND_UP_ARS} on the way UP —
 * deliberately asymmetric (decisión del founder, 2026-08-19): quicker to pass along a price drop
 * than to raise prices, so a borderline move errs toward keeping tenants' price stable or cheaper,
 * never toward nickel-and-diming them on noise. Either way this is a band, not a live peg, so
 * prices don't churn on every peso of exchange-rate movement. When the band breaks, every
 * {@link PlanPricing} row is recomputed from its own locked {@code usd_equivalent} (not from the
 * old {@code ars_price}) and rounded to the nearest {@value #ROUNDING_UNIT_ARS} pesos — a clean
 * number on an invoice, not something like $23.342 — and the new rate becomes the reference for
 * the next check. Global, not per-tenant — one reference rate for the whole platform (see README
 * pricing notes).
 */
@Component
public class PlanPricingScheduler {

	private static final Logger log = LoggerFactory.getLogger(PlanPricingScheduler.class);
	private static final BigDecimal BAND_DOWN_ARS = new BigDecimal("115");
	private static final BigDecimal BAND_UP_ARS = new BigDecimal("130");
	private static final BigDecimal ROUNDING_UNIT_ARS = new BigDecimal("100");

	private final DolarBlueClient dolarBlueClient;
	private final PlatformSettingsRepository platformSettingsRepository;
	private final PlanPricingRepository planPricingRepository;

	public PlanPricingScheduler(DolarBlueClient dolarBlueClient, PlatformSettingsRepository platformSettingsRepository,
			PlanPricingRepository planPricingRepository) {
		this.dolarBlueClient = dolarBlueClient;
		this.platformSettingsRepository = platformSettingsRepository;
		this.planPricingRepository = planPricingRepository;
	}

	@Scheduled(fixedDelayString = "${app.pricing.blue-rate-check-interval-ms:86400000}")
	@Transactional
	public void checkAndReindex() {
		BigDecimal currentBlue = dolarBlueClient.fetchBlueRate().venta();
		BigDecimal reference = platformSettingsRepository.getReferenceBlueRate();
		// Signed, not abs() — which band applies depends on the direction of the move, not just its
		// size (see the class Javadoc for why they're deliberately not the same number).
		BigDecimal drift = currentBlue.subtract(reference);
		BigDecimal requiredBand = drift.signum() < 0 ? BAND_DOWN_ARS : BAND_UP_ARS;
		if (drift.abs().compareTo(requiredBand) < 0) {
			return;
		}
		log.info("Dólar blue moved {} away from reference {} (now {}, banda {}) — reindexing plan prices", drift,
				reference, currentBlue, requiredBand);
		for (PlanPricing pricing : planPricingRepository.findAll()) {
			BigDecimal rawPrice = pricing.getUsdEquivalent().multiply(currentBlue);
			BigDecimal newPrice = roundToNearest(rawPrice, ROUNDING_UNIT_ARS);
			log.info("{}: {} -> {}", pricing.getPlanTier(), pricing.getArsPrice(), newPrice);
			pricing.setArsPrice(newPrice);
		}
		platformSettingsRepository.updateReferenceBlueRate(currentBlue);
	}

	private BigDecimal roundToNearest(BigDecimal value, BigDecimal unit) {
		return value.divide(unit, 0, RoundingMode.HALF_UP).multiply(unit);
	}
}
