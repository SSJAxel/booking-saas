package dev.capibyte.bookingsaas.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.capibyte.bookingsaas.tenant.dto.DolarBlueRate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain Mockito unit test, same style as SubscriptionServiceTest — no Spring context, no real
 * HTTP call (DolarBlueClient itself is mocked, its own contract is covered by
 * DolarBlueClientTest). */
class PlanPricingSchedulerTest {

	private final DolarBlueClient dolarBlueClient = mock(DolarBlueClient.class);
	private final PlatformSettingsRepository platformSettingsRepository = mock(PlatformSettingsRepository.class);
	private final PlanPricingRepository planPricingRepository = mock(PlanPricingRepository.class);
	private final PlanPricingScheduler scheduler = new PlanPricingScheduler(dolarBlueClient,
			platformSettingsRepository, planPricingRepository);

	@Test
	void reindexesWhenBlueMovesUpPastTheUpBandAndRoundsToTheNearestHundred() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		// +135 from reference — past the 130 up band.
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1605"), new BigDecimal("1615")));
		PlanPricing pro = pricing(PlanTier.PRO, "50000.00", "33.7838");
		when(planPricingRepository.findAll()).thenReturn(List.of(pro));

		scheduler.checkAndReindex();

		// usd_equivalent (33.7838) * new blue (1615) = 54560.837 -> nearest 100 = 54600.
		org.assertj.core.api.Assertions.assertThat(pro.getArsPrice()).isEqualByComparingTo("54600");
		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1615"));
	}

	@Test
	void reindexesWhenBlueMovesDownPastTheDownBandAndRoundsToTheNearestHundred() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		// -120 from reference — past the 115 down band.
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1355"), new BigDecimal("1360")));
		PlanPricing basic = pricing(PlanTier.BASIC, "30000.00", "20.2703");
		when(planPricingRepository.findAll()).thenReturn(List.of(basic));

		scheduler.checkAndReindex();

		// usd_equivalent (20.2703) * new blue (1360) = 27567.608 -> nearest 100 = 27600.
		org.assertj.core.api.Assertions.assertThat(basic.getArsPrice()).isEqualByComparingTo("27600");
		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1360"));
	}

	@Test
	void doesNothingWhenBlueStaysInsideBothBands() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1550"), new BigDecimal("1560")));

		scheduler.checkAndReindex();

		verify(planPricingRepository, never()).findAll();
		verify(platformSettingsRepository, never()).updateReferenceBlueRate(org.mockito.ArgumentMatchers.any());
	}

	/** The whole point of splitting the band (2026-08-19, founder's call): an upward move that would
	 * have tripped the old symmetric 115 threshold must NOT reindex now that the up band is 130 —
	 * quicker to pass along a drop, slower to raise prices. */
	@Test
	void anUpwardMoveInsideTheNewWiderUpBandDoesNotReindexEvenThoughItWouldHavePassedTheOldSymmetricBand() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		// +120 — past the old symmetric 115 band, but short of the new 130 up band.
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1590"), new BigDecimal("1600")));

		scheduler.checkAndReindex();

		verify(planPricingRepository, never()).findAll();
		verify(platformSettingsRepository, never()).updateReferenceBlueRate(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void driftOfExactlyTheDownBandWidthTriggersReindex() {
		// 1480 - 115 = 1365 — boundary case, ">= band" should trigger, not just "> band".
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1360"), new BigDecimal("1365")));
		when(planPricingRepository.findAll()).thenReturn(List.of());

		scheduler.checkAndReindex();

		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1365"));
	}

	@Test
	void driftOfExactlyTheUpBandWidthTriggersReindex() {
		// 1480 + 130 = 1610 — boundary case, ">= band" should trigger, not just "> band".
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1600"), new BigDecimal("1610")));
		when(planPricingRepository.findAll()).thenReturn(List.of());

		scheduler.checkAndReindex();

		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1610"));
	}

	private PlanPricing pricing(PlanTier tier, String arsPrice, String usdEquivalent) {
		PlanPricing pricing = new PlanPricing();
		pricing.setPlanTier(tier);
		pricing.setArsPrice(new BigDecimal(arsPrice));
		pricing.setUsdEquivalent(new BigDecimal(usdEquivalent));
		return pricing;
	}
}
