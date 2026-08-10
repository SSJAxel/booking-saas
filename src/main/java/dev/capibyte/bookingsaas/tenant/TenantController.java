package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.payment.MercadoPagoAccountService;
import dev.capibyte.bookingsaas.payment.SubscriptionService;
import dev.capibyte.bookingsaas.payment.dto.OAuthConnectResponse;
import dev.capibyte.bookingsaas.payment.dto.SubscriptionCheckoutResponse;
import dev.capibyte.bookingsaas.tenant.dto.BrandingUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.ClientRankingSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.NotificationSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.PlanChangeRequest;
import dev.capibyte.bookingsaas.tenant.dto.TenantResponse;
import dev.capibyte.bookingsaas.tenant.dto.TimezoneUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Self-service settings for the caller's own tenant — the tenant-level counterpart to /api/me. */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;
	private final SubscriptionService subscriptionService;
	private final MercadoPagoAccountService mercadoPagoAccountService;

	@GetMapping
	@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
	public TenantResponse get() {
		return TenantResponse.from(tenantService.findById(TenantContext.getTenantId()));
	}

	/**
	 * Owner/admin: logo, accent color, tagline shown on this tenant's public booking page, plus the
	 * contact email/WhatsApp number behind the owner panel's quick-access buttons.
	 */
	@PatchMapping("/branding")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateBranding(@Valid @RequestBody BrandingUpdateRequest request) {
		return TenantResponse.from(tenantService.updateBranding(TenantContext.getTenantId(), request.logoUrl(),
				request.accentColor(), request.tagline(), request.contactEmail(), request.whatsappNumber(),
				request.transferAlias()));
	}

	/**
	 * Owner/admin: the IANA zone (e.g. "America/Argentina/Buenos_Aires") every wall-clock time for
	 * this tenant is interpreted in — weekly availability hours, booking requests, the "which day"
	 * an appointment counts toward. Defaults to UTC at registration.
	 */
	@PatchMapping("/timezone")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateTimezone(@Valid @RequestBody TimezoneUpdateRequest request) {
		return TenantResponse.from(tenantService.updateTimezone(TenantContext.getTenantId(), request.timezone()));
	}

	/**
	 * Owner/admin: turns the WhatsApp channel on/off for this tenant. Off by default — email alone
	 * is always enough for a booking to work, this just adds a second channel on top of it.
	 */
	@PatchMapping("/notifications")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateNotifications(@Valid @RequestBody NotificationSettingsRequest request) {
		return TenantResponse
				.from(tenantService.updateWhatsAppEnabled(TenantContext.getTenantId(), request.whatsappEnabled()));
	}

	/**
	 * Owner/admin: how the "mejores clientes" panel filters and caps the ranking — the minimum
	 * rating to qualify, and at most how many to show (1–15).
	 */
	@PatchMapping("/client-ranking")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateClientRanking(@Valid @RequestBody ClientRankingSettingsRequest request) {
		return TenantResponse.from(tenantService.updateClientRankingSettings(TenantContext.getTenantId(),
				request.topClientsThreshold(), request.topClientsCount()));
	}

	/**
	 * Owner-only: where to send their browser to connect this tenant's own MercadoPago account
	 * (OAuth Connect), so their checkouts/subscriptions pay out to them instead of the shared
	 * platform account. See MercadoPagoOAuthController for where the flow lands after they approve.
	 */
	@GetMapping("/mercadopago/connect")
	@PreAuthorize("hasRole('OWNER')")
	public OAuthConnectResponse connectMercadoPago() {
		return new OAuthConnectResponse(mercadoPagoAccountService.buildAuthorizationUrl(TenantContext.getTenantId()));
	}

	/** Owner-only: starts a MercadoPago subscription for a paid plan — this is the upgrade path. */
	@PostMapping("/subscription")
	@PreAuthorize("hasRole('OWNER')")
	@ResponseStatus(HttpStatus.CREATED)
	public SubscriptionCheckoutResponse subscribe(@Valid @RequestBody PlanChangeRequest request) {
		return subscriptionService.subscribe(TenantContext.getTenantId(), request.planTier());
	}

	/**
	 * Owner-only: only accepts a free tier (see TenantService.changePlan) — this is the
	 * downgrade/cancellation path. tenantService.changePlan runs (and validates) first so an
	 * invalid request (e.g. trying to PATCH straight to a paid tier) never triggers the
	 * cancellation side effect below it.
	 */
	@PatchMapping("/plan")
	@PreAuthorize("hasRole('OWNER')")
	public TenantResponse changePlan(@Valid @RequestBody PlanChangeRequest request) {
		Tenant tenant = tenantService.changePlan(TenantContext.getTenantId(), request.planTier());
		subscriptionService.cancelActiveSubscription(TenantContext.getTenantId());
		return TenantResponse.from(tenant);
	}
}
