package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoOAuthToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which MercadoPago access token a given tenant's checkouts/subscriptions should use —
 * their own connected account (OAuth Connect) if they have one, the platform's shared token
 * otherwise. That fallback is deliberate: connecting is additive, not a hard requirement, so a
 * tenant mid-onboarding (or every tenant, before this feature existed) keeps working exactly like
 * before.
 */
@Service
public class MercadoPagoAccountService {

	private final MercadoPagoAccountRepository accountRepository;
	private final MercadoPagoClient mercadoPagoClient;
	private final String platformAccessToken;
	private final String oauthRedirectUri;
	private final String connectReturnUrl;

	public MercadoPagoAccountService(MercadoPagoAccountRepository accountRepository,
			MercadoPagoClient mercadoPagoClient,
			@Value("${app.mercadopago.access-token}") String platformAccessToken,
			@Value("${app.mercadopago.oauth-redirect-uri}") String oauthRedirectUri,
			@Value("${app.mercadopago.connect-return-url}") String connectReturnUrl) {
		this.accountRepository = accountRepository;
		this.mercadoPagoClient = mercadoPagoClient;
		this.platformAccessToken = platformAccessToken;
		this.oauthRedirectUri = oauthRedirectUri;
		this.connectReturnUrl = connectReturnUrl;
	}

	public String getPlatformAccessToken() {
		return platformAccessToken;
	}

	public String buildAuthorizationUrl(UUID tenantId) {
		return mercadoPagoClient.buildAuthorizationUrl(tenantId.toString(), oauthRedirectUri);
	}

	/**
	 * Deliberately NOT @Transactional at this level — same reasoning as the webhook handlers in
	 * PaymentService/SubscriptionService: the tenant is only known after parsing {@code state}, so
	 * TenantContext must be set before any @TenantId-scoped query runs, not partway through an
	 * already-open transaction. Returns where to send the browser next.
	 */
	public String handleOAuthCallback(String code, String state) {
		UUID tenantId;
		try {
			tenantId = UUID.fromString(state);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Malformed OAuth state: " + state);
		}

		MercadoPagoOAuthToken token = mercadoPagoClient.exchangeCodeForToken(code, oauthRedirectUri);

		TenantContext.setTenantId(tenantId);
		try {
			MercadoPagoAccount account = accountRepository.findFirstByOrderByCreatedAtDesc().orElseGet(MercadoPagoAccount::new);
			account.setMercadoPagoUserId(String.valueOf(token.userId()));
			account.setAccessToken(token.accessToken());
			account.setRefreshToken(token.refreshToken());
			account.setPublicKey(token.publicKey());
			account.setExpiresAt(Instant.now().plusSeconds(token.expiresInSeconds()));
			accountRepository.save(account);
		} finally {
			TenantContext.clear();
		}
		return connectReturnUrl;
	}

	/**
	 * Refreshes the stored token first if it's expired, so callers never hand MercadoPagoClient a
	 * token that's about to be rejected. Assumes TenantContext is already set by the caller (this
	 * runs mid-request, inside PaymentService/SubscriptionService, both of which already operate
	 * within an already-resolved tenant).
	 */
	@Transactional
	public String resolveAccessToken(UUID tenantId) {
		Optional<MercadoPagoAccount> maybeAccount = accountRepository.findFirstByOrderByCreatedAtDesc();
		if (maybeAccount.isEmpty()) {
			return platformAccessToken;
		}

		MercadoPagoAccount account = maybeAccount.get();
		if (Instant.now().isAfter(account.getExpiresAt())) {
			MercadoPagoOAuthToken refreshed = mercadoPagoClient.refreshAccessToken(account.getRefreshToken());
			account.setAccessToken(refreshed.accessToken());
			account.setRefreshToken(refreshed.refreshToken());
			account.setExpiresAt(Instant.now().plusSeconds(refreshed.expiresInSeconds()));
		}
		return account.getAccessToken();
	}

	@Transactional(readOnly = true)
	public boolean isConnected(UUID tenantId) {
		return accountRepository.findFirstByOrderByCreatedAtDesc().isPresent();
	}

	/**
	 * Unlinks this tenant's own MercadoPago account — after this, {@link #resolveAccessToken} falls
	 * back to the shared platform token again, same as a tenant who never connected one. Doesn't
	 * revoke the OAuth grant on MercadoPago's side (no API for that), just forgets the token here;
	 * the tenant can revoke access from their own MercadoPago account settings if they want to be
	 * thorough about it.
	 */
	@Transactional
	public void disconnect(UUID tenantId) {
		accountRepository.findFirstByOrderByCreatedAtDesc().ifPresent(accountRepository::delete);
	}
}
