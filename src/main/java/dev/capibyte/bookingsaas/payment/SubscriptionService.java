package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.common.UnauthorizedException;
import dev.capibyte.bookingsaas.identity.AppUser;
import dev.capibyte.bookingsaas.identity.AppUserRepository;
import dev.capibyte.bookingsaas.identity.Role;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreapproval;
import dev.capibyte.bookingsaas.payment.dto.SubscriptionCheckoutResponse;
import dev.capibyte.bookingsaas.tenant.PlanPricingService;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What actually gates PlanTier behind payment — TenantService.changePlan alone lets anyone flip
 * to a free tier, but only a MercadoPago-authorized subscription can move a tenant onto a paid
 * one. Structured the same way as PaymentService (see its Javadoc for why this isn't
 * @RequiredArgsConstructor, and why handleWebhook itself isn't @Transactional).
 */
@Service
public class SubscriptionService {

	private static final List<SubscriptionStatus> CANCELLABLE_STATUSES =
			List.of(SubscriptionStatus.PENDING, SubscriptionStatus.AUTHORIZED, SubscriptionStatus.PAUSED);

	private final SubscriptionRepository subscriptionRepository;
	private final TenantService tenantService;
	private final AppUserRepository appUserRepository;
	private final MercadoPagoClient mercadoPagoClient;
	private final MercadoPagoAccountService mercadoPagoAccountService;
	private final WebhookSignatureVerifier signatureVerifier;
	private final PlanPricingService planPricingService;
	private final String webhookSecret;

	public SubscriptionService(SubscriptionRepository subscriptionRepository, TenantService tenantService,
			AppUserRepository appUserRepository, MercadoPagoClient mercadoPagoClient,
			MercadoPagoAccountService mercadoPagoAccountService, WebhookSignatureVerifier signatureVerifier,
			PlanPricingService planPricingService, @Value("${app.mercadopago.webhook-secret}") String webhookSecret) {
		this.subscriptionRepository = subscriptionRepository;
		this.tenantService = tenantService;
		this.appUserRepository = appUserRepository;
		this.mercadoPagoClient = mercadoPagoClient;
		this.mercadoPagoAccountService = mercadoPagoAccountService;
		this.signatureVerifier = signatureVerifier;
		this.planPricingService = planPricingService;
		this.webhookSecret = webhookSecret;
	}

	@Transactional
	public SubscriptionCheckoutResponse subscribe(UUID tenantId, PlanTier requestedTier) {
		if (requestedTier.isFree()) {
			throw new BadRequestException(requestedTier + " is free — use PATCH /api/tenant/plan instead");
		}
		BigDecimal price = planPricingService.currentPrice(requestedTier);
		if (price == null) {
			throw new BadRequestException(requestedTier + " doesn't have a price set yet — not available to subscribe to");
		}
		Tenant tenant = tenantService.findById(tenantId);
		AppUser owner = appUserRepository.findFirstByRole(Role.OWNER)
				.orElseThrow(() -> new NotFoundException("No owner found for tenant: " + tenantId));

		Subscription subscription = new Subscription();
		subscription.setPlanTier(requestedTier);
		subscription.setAmount(price);
		subscription = subscriptionRepository.save(subscription);

		String accessToken = mercadoPagoAccountService.resolveAccessToken(tenantId);
		MercadoPagoPreapproval preapproval = mercadoPagoClient.createPreapproval(accessToken, tenantId,
				subscription.getId(), "booking-saas " + tenant.getSlug() + " — plan " + requestedTier, price,
				owner.getEmail());
		subscription.setProviderSubscriptionId(preapproval.id());

		return new SubscriptionCheckoutResponse(subscription.getId(), preapproval.initPoint());
	}

	/**
	 * Deliberately NOT @Transactional at this level — same reasoning as PaymentService.handleWebhook:
	 * the tenant isn't known until the preapproval is fetched and its external_reference parsed, so
	 * TenantContext has to be set before any @TenantId-scoped repository call, not partway through
	 * one already-open transaction. Re-fetches with the platform token for the same reason
	 * PaymentService.handleWebhook does — see its Javadoc.
	 */
	public void handleWebhook(String preapprovalId, String requestId, String signatureHeader) {
		if (!signatureVerifier.isValid(preapprovalId, requestId, signatureHeader, webhookSecret)) {
			throw new UnauthorizedException("Invalid MercadoPago webhook signature");
		}

		MercadoPagoPreapproval preapproval =
				mercadoPagoClient.getPreapproval(mercadoPagoAccountService.getPlatformAccessToken(), preapprovalId);
		String[] reference = parseExternalReference(preapproval.externalReference());
		UUID tenantId = UUID.fromString(reference[0]);
		UUID subscriptionId = UUID.fromString(reference[1]);

		TenantContext.setTenantId(tenantId);
		try {
			Subscription subscription = subscriptionRepository.findById(subscriptionId)
					.orElseThrow(() -> new NotFoundException("Subscription not found: " + subscriptionId));
			SubscriptionStatus mappedStatus = mapStatus(preapproval.status());
			subscription.setStatus(mappedStatus);
			subscriptionRepository.save(subscription);

			if (mappedStatus == SubscriptionStatus.AUTHORIZED) {
				tenantService.applyPlanTierFromSubscription(tenantId, subscription.getPlanTier());
			} else if (mappedStatus == SubscriptionStatus.CANCELLED || mappedStatus == SubscriptionStatus.PAUSED) {
				// A failed/cancelled/paused charge means the tenant stops getting what they stopped
				// paying for — not left stuck on PRO forever. This intentionally doesn't cover an
				// individual missed recurring charge that MercadoPago is still retrying (that's the
				// "authorized_payment" webhook topic, not "subscription_preapproval" — not handled
				// here yet, see README roadmap).
				tenantService.applyPlanTierFromSubscription(tenantId, PlanTier.PERSONAL);
			}
		} finally {
			TenantContext.clear();
		}
	}

	/** Called when a tenant downgrades to a free tier by hand — stop billing them for the old one. */
	@Transactional
	public void cancelActiveSubscription(UUID tenantId) {
		subscriptionRepository.findFirstByStatusInOrderByCreatedAtDesc(CANCELLABLE_STATUSES).ifPresent(subscription -> {
			if (subscription.getProviderSubscriptionId() != null) {
				String accessToken = mercadoPagoAccountService.resolveAccessToken(tenantId);
				mercadoPagoClient.cancelPreapproval(accessToken, subscription.getProviderSubscriptionId());
			}
			subscription.setStatus(SubscriptionStatus.CANCELLED);
		});
	}

	private String[] parseExternalReference(String externalReference) {
		String[] parts = externalReference.split(":", 2);
		if (parts.length != 2) {
			throw new BadRequestException("Malformed external_reference from MercadoPago: " + externalReference);
		}
		return parts;
	}

	private SubscriptionStatus mapStatus(String mercadoPagoStatus) {
		return switch (mercadoPagoStatus) {
			case "authorized" -> SubscriptionStatus.AUTHORIZED;
			case "paused" -> SubscriptionStatus.PAUSED;
			case "cancelled" -> SubscriptionStatus.CANCELLED;
			default -> SubscriptionStatus.PENDING;
		};
	}
}
