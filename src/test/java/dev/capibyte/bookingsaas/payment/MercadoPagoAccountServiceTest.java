package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoOAuthToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MercadoPagoAccountServiceTest {

	private final MercadoPagoAccountRepository accountRepository = mock(MercadoPagoAccountRepository.class);
	private final MercadoPagoClient mercadoPagoClient = mock(MercadoPagoClient.class);
	private final MercadoPagoAccountService accountService = new MercadoPagoAccountService(accountRepository,
			mercadoPagoClient, "platform-access-token", "https://example.com/oauth/callback",
			"https://example.com/tenant");

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void buildAuthorizationUrlUsesTheTenantIdAsState() {
		UUID tenantId = UUID.randomUUID();
		when(mercadoPagoClient.buildAuthorizationUrl(tenantId.toString(), "https://example.com/oauth/callback"))
				.thenReturn("https://auth.mercadopago.com/authorization?state=" + tenantId);

		String url = accountService.buildAuthorizationUrl(tenantId);

		assertThat(url).contains(tenantId.toString());
	}

	@Test
	void handleOAuthCallbackRejectsAMalformedState() {
		assertThatThrownBy(() -> accountService.handleOAuthCallback("code-1", "not-a-uuid"))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void handleOAuthCallbackStoresTheAccountAndReturnsTheReturnUrl() {
		UUID tenantId = UUID.randomUUID();
		when(mercadoPagoClient.exchangeCodeForToken("code-1", "https://example.com/oauth/callback"))
				.thenReturn(new MercadoPagoOAuthToken("seller-token", "seller-refresh", "pub-key", 555L, 15552000L));
		when(accountRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		when(accountRepository.save(any(MercadoPagoAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String returnUrl = accountService.handleOAuthCallback("code-1", tenantId.toString());

		assertThat(returnUrl).isEqualTo("https://example.com/tenant");
		assertThat(TenantContext.getTenantId()).isNull();
		verify(accountRepository).save(any(MercadoPagoAccount.class));
	}

	@Test
	void resolveAccessTokenFallsBackToThePlatformTokenWhenNotConnected() {
		when(accountRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

		String token = accountService.resolveAccessToken(UUID.randomUUID());

		assertThat(token).isEqualTo("platform-access-token");
	}

	@Test
	void resolveAccessTokenUsesTheStoredTokenWhenStillValid() {
		MercadoPagoAccount account = new MercadoPagoAccount();
		account.setAccessToken("still-valid-token");
		account.setExpiresAt(Instant.now().plusSeconds(3600));
		when(accountRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(account));

		String token = accountService.resolveAccessToken(UUID.randomUUID());

		assertThat(token).isEqualTo("still-valid-token");
		verify(mercadoPagoClient, never()).refreshAccessToken(anyString());
	}

	@Test
	void resolveAccessTokenRefreshesAnExpiredToken() {
		MercadoPagoAccount account = new MercadoPagoAccount();
		account.setAccessToken("expired-token");
		account.setRefreshToken("refresh-1");
		account.setExpiresAt(Instant.now().minusSeconds(60));
		when(accountRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(account));
		when(mercadoPagoClient.refreshAccessToken("refresh-1"))
				.thenReturn(new MercadoPagoOAuthToken("refreshed-token", "refreshed-refresh", null, 555L, 15552000L));

		String token = accountService.resolveAccessToken(UUID.randomUUID());

		assertThat(token).isEqualTo("refreshed-token");
		assertThat(account.getRefreshToken()).isEqualTo("refreshed-refresh");
	}
}
