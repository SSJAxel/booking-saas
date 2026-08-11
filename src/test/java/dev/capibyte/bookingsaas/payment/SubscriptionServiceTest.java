package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.common.UnauthorizedException;
import dev.capibyte.bookingsaas.identity.AppUser;
import dev.capibyte.bookingsaas.identity.AppUserRepository;
import dev.capibyte.bookingsaas.identity.Role;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreapproval;
import dev.capibyte.bookingsaas.payment.dto.SubscriptionCheckoutResponse;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {

	private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
	private final TenantService tenantService = mock(TenantService.class);
	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
	private final MercadoPagoClient mercadoPagoClient = mock(MercadoPagoClient.class);
	private final MercadoPagoAccountService mercadoPagoAccountService = mock(MercadoPagoAccountService.class);
	private final WebhookSignatureVerifier signatureVerifier = mock(WebhookSignatureVerifier.class);
	private final SubscriptionService subscriptionService = new SubscriptionService(subscriptionRepository,
			tenantService, appUserRepository, mercadoPagoClient, mercadoPagoAccountService, signatureVerifier,
			"test-webhook-secret");

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void subscribeRejectsAFreeTier() {
		assertThatThrownBy(() -> subscriptionService.subscribe(UUID.randomUUID(), PlanTier.TRIAL))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void subscribeCreatesAPendingSubscriptionAndReturnsTheCheckoutUrl() {
		UUID tenantId = UUID.randomUUID();
		Tenant tenant = new Tenant();
		tenant.setSlug("demo-studio");
		when(tenantService.findById(tenantId)).thenReturn(tenant);

		AppUser owner = new AppUser();
		owner.setEmail("owner@demo.com");
		when(appUserRepository.findFirstByRole(Role.OWNER)).thenReturn(Optional.of(owner));

		when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
			Subscription saved = invocation.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});
		when(mercadoPagoAccountService.resolveAccessToken(tenantId)).thenReturn("tenant-access-token");
		when(mercadoPagoClient.createPreapproval(eq("tenant-access-token"), eq(tenantId), any(), anyString(),
				eq(PlanTier.PRO.getMonthlyPrice()), eq("owner@demo.com")))
				.thenReturn(new MercadoPagoPreapproval("preapproval-1", "pending",
						"https://mp.example/subscriptions/preapproval-1", "ignored"));

		SubscriptionCheckoutResponse response = subscriptionService.subscribe(tenantId, PlanTier.PRO);

		assertThat(response.checkoutUrl()).isEqualTo("https://mp.example/subscriptions/preapproval-1");
		assertThat(response.subscriptionId()).isNotNull();
	}

	@Test
	void handleWebhookRejectsAnInvalidSignature() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(false);

		assertThatThrownBy(() -> subscriptionService.handleWebhook("preapproval-1", "req-1", "bad-signature"))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void handleWebhookUpgradesTheTenantWhenAuthorized() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(true);

		UUID tenantId = UUID.randomUUID();
		UUID subscriptionId = UUID.randomUUID();
		when(mercadoPagoAccountService.getPlatformAccessToken()).thenReturn("platform-access-token");
		when(mercadoPagoClient.getPreapproval("platform-access-token", "preapproval-1"))
				.thenReturn(new MercadoPagoPreapproval("preapproval-1", "authorized", null, tenantId + ":" + subscriptionId));

		Subscription subscription = new Subscription();
		subscription.setPlanTier(PlanTier.PRO);
		when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
		when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Tenant tenant = new Tenant();
		tenant.setPlanTier(PlanTier.BASIC);
		when(tenantService.findById(tenantId)).thenReturn(tenant);

		subscriptionService.handleWebhook("preapproval-1", "req-1", "valid-signature");

		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.AUTHORIZED);
		assertThat(tenant.getPlanTier()).isEqualTo(PlanTier.PRO);
		assertThat(TenantContext.getTenantId()).isNull();
	}

	@Test
	void handleWebhookDowngradesTheTenantWhenCancelled() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(true);

		UUID tenantId = UUID.randomUUID();
		UUID subscriptionId = UUID.randomUUID();
		when(mercadoPagoAccountService.getPlatformAccessToken()).thenReturn("platform-access-token");
		when(mercadoPagoClient.getPreapproval("platform-access-token", "preapproval-1"))
				.thenReturn(new MercadoPagoPreapproval("preapproval-1", "cancelled", null, tenantId + ":" + subscriptionId));

		Subscription subscription = new Subscription();
		subscription.setPlanTier(PlanTier.PRO);
		when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
		when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Tenant tenant = new Tenant();
		tenant.setPlanTier(PlanTier.PRO);
		when(tenantService.findById(tenantId)).thenReturn(tenant);

		subscriptionService.handleWebhook("preapproval-1", "req-1", "valid-signature");

		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
		assertThat(tenant.getPlanTier()).isEqualTo(PlanTier.PERSONAL);
	}

	@Test
	void cancelActiveSubscriptionCancelsAtMercadoPagoWhenOneExists() {
		Subscription subscription = new Subscription();
		subscription.setStatus(SubscriptionStatus.AUTHORIZED);
		subscription.setProviderSubscriptionId("preapproval-1");
		when(subscriptionRepository.findFirstByStatusInOrderByCreatedAtDesc(
				List.of(SubscriptionStatus.PENDING, SubscriptionStatus.AUTHORIZED, SubscriptionStatus.PAUSED)))
				.thenReturn(Optional.of(subscription));
		when(mercadoPagoAccountService.resolveAccessToken(any())).thenReturn("tenant-access-token");

		subscriptionService.cancelActiveSubscription(UUID.randomUUID());

		verify(mercadoPagoClient).cancelPreapproval("tenant-access-token", "preapproval-1");
		assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
	}

	@Test
	void cancelActiveSubscriptionIsANoOpWhenNoneExists() {
		when(subscriptionRepository.findFirstByStatusInOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

		subscriptionService.cancelActiveSubscription(UUID.randomUUID());

		verify(mercadoPagoClient, never()).cancelPreapproval(anyString(), anyString());
	}
}
