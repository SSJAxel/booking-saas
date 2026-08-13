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
 * to: the real rate has to drift at least {@value #BAND_ARS} pesos away from
 * {@code platform_settings.reference_blue_rate} before anything changes — this is deliberately a
 * band, not a live peg, so prices don't churn on every peso of exchange-rate noise. When the band
 * breaks, every {@link PlanPricing} row is recomputed from its own locked
 * {@code usd_equivalent} (not from the old {@code ars_price}), and the new rate becomes the
 * reference for the next check. Global, not per-tenant — one reference rate for the whole
 * platform (see README pricing notes).
 */
@Component
public class PlanPricingScheduler {

	private static final Logger log = LoggerFactory.getLogger(PlanPricingScheduler.class);
	private static final BigDecimal BAND_ARS = new BigDecimal("115");

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
		BigDecimal drift = currentBlue.subtract(reference).abs();
		if (drift.compareTo(BAND_ARS) < 0) {
			return;
		}
		log.info("Dólar blue moved {} away from reference {} (now {}) — reindexing plan prices", drift, reference,
				currentBlue);
		for (PlanPricing pricing : planPricingRepository.findAll()) {
			BigDecimal newPrice = pricing.getUsdEquivalent().multiply(currentBlue).setScale(0, RoundingMode.HALF_UP);
			log.info("{}: {} -> {}", pricing.getPlanTier(), pricing.getArsPrice(), newPrice);
			pricing.setArsPrice(newPrice);
		}
		platformSettingsRepository.updateReferenceBlueRate(currentBlue);
	}
}
