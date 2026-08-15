package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantServiceTest {

	private final TenantRepository tenantRepository = mock(TenantRepository.class);
	private final RewardTierRepository rewardTierRepository = mock(RewardTierRepository.class);
	private final TenantService tenantService = new TenantService(tenantRepository, rewardTierRepository, 30);

	@Test
	void applyPlanTierFromSubscriptionAppliesTheTierWhenNotManuallySet() {
		UUID tenantId = UUID.randomUUID();
		Tenant tenant = new Tenant();
		tenant.setPlanTier(PlanTier.PERSONAL);
		when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

		tenantService.applyPlanTierFromSubscription(tenantId, PlanTier.PRO);

		assertThat(tenant.getPlanTier()).isEqualTo(PlanTier.PRO);
	}

	@Test
	void applyPlanTierFromSubscriptionSkipsWhenPlanWasManuallySet() {
		UUID tenantId = UUID.randomUUID();
		Tenant tenant = new Tenant();
		tenant.setPlanTier(PlanTier.MAX);
		tenant.setPlanManuallySet(true);
		when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

		tenantService.applyPlanTierFromSubscription(tenantId, PlanTier.PERSONAL);

		assertThat(tenant.getPlanTier()).isEqualTo(PlanTier.MAX);
	}
}
