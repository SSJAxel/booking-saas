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
	void reindexesWhenBlueMovesUpPastTheBand() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1590"), new BigDecimal("1600")));
		PlanPricing pro = pricing(PlanTier.PRO, "50000.00", "33.7838");
		when(planPricingRepository.findAll()).thenReturn(List.of(pro));

		scheduler.checkAndReindex();

		// usd_equivalent (33.7838) * new blue (1600) = 54054.08 -> rounded to nearest peso.
		org.assertj.core.api.Assertions.assertThat(pro.getArsPrice()).isEqualByComparingTo("54054");
		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1600"));
	}

	@Test
	void reindexesWhenBlueMovesDownPastTheBand() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1355"), new BigDecimal("1360")));
		PlanPricing basic = pricing(PlanTier.BASIC, "30000.00", "20.2703");
		when(planPricingRepository.findAll()).thenReturn(List.of(basic));

		scheduler.checkAndReindex();

		org.assertj.core.api.Assertions.assertThat(basic.getArsPrice()).isEqualByComparingTo("27568");
		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1360"));
	}

	@Test
	void doesNothingWhenBlueStaysInsideTheBand() {
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1550"), new BigDecimal("1560")));

		scheduler.checkAndReindex();

		verify(planPricingRepository, never()).findAll();
		verify(platformSettingsRepository, never()).updateReferenceBlueRate(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void driftOfExactlyTheBandWidthTriggersReindex() {
		// 1480 + 115 = 1595 — boundary case, ">= band" should trigger, not just "> band".
		when(platformSettingsRepository.getReferenceBlueRate()).thenReturn(new BigDecimal("1480.00"));
		when(dolarBlueClient.fetchBlueRate()).thenReturn(new DolarBlueRate(new BigDecimal("1590"), new BigDecimal("1595")));
		when(planPricingRepository.findAll()).thenReturn(List.of());

		scheduler.checkAndReindex();

		verify(platformSettingsRepository).updateReferenceBlueRate(new BigDecimal("1595"));
	}

	private PlanPricing pricing(PlanTier tier, String arsPrice, String usdEquivalent) {
		PlanPricing pricing = new PlanPricing();
		pricing.setPlanTier(tier);
		pricing.setArsPrice(new BigDecimal(arsPrice));
		pricing.setUsdEquivalent(new BigDecimal(usdEquivalent));
		return pricing;
	}
}
