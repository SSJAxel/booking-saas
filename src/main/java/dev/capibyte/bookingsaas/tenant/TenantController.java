package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.FileStorageService;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.payment.MercadoPagoAccountService;
import dev.capibyte.bookingsaas.payment.SubscriptionService;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoConnectionStatusResponse;
import dev.capibyte.bookingsaas.payment.dto.OAuthConnectResponse;
import dev.capibyte.bookingsaas.payment.dto.SubscriptionCheckoutResponse;
import dev.capibyte.bookingsaas.tenant.dto.BirthdayMessageUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.BrandingUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.ClientRankingSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.CommissionsSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.DepositExpirationUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.HistoryRetentionUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.LoyaltyRewardsSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.MercadoPagoFeeUpdateRequest;
import dev.capibyte.bookingsaas.tenant.dto.NotificationSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.PlanChangeRequest;
import dev.capibyte.bookingsaas.tenant.dto.ReviewsSettingsRequest;
import dev.capibyte.bookingsaas.tenant.dto.TenantResponse;
import dev.capibyte.bookingsaas.tenant.dto.TimezoneUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Self-service settings for the caller's own tenant — the tenant-level counterpart to /api/me. */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;
	private final SubscriptionService subscriptionService;
	private final MercadoPagoAccountService mercadoPagoAccountService;
	private final FileStorageService fileStorageService;

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
				request.bannerUrl(), request.accentColor(), request.tagline(), request.contactEmail(),
				request.whatsappNumber(), request.transferAlias(), request.instagramUrl(), request.facebookUrl(),
				request.instagramFeedUrl()));
	}

	/** Owner/admin: uploads a logo image file, stores it, and points logoUrl at it — an
	 * alternative to PATCH /branding for tenants who don't already have the image hosted
	 * somewhere with a URL to paste. */
	@PostMapping(value = "/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse uploadLogo(@RequestParam("file") MultipartFile file) {
		String relativePath = fileStorageService.store(file, "public/tenant-logos");
		return TenantResponse.from(tenantService.updateLogoUrl(TenantContext.getTenantId(), "/uploads/" + relativePath));
	}

	/** Owner/admin: same as uploadLogo, for the public page's cover/banner image. */
	@PostMapping(value = "/branding/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse uploadBanner(@RequestParam("file") MultipartFile file) {
		String relativePath = fileStorageService.store(file, "public/tenant-banners");
		return TenantResponse
				.from(tenantService.updateBannerUrl(TenantContext.getTenantId(), "/uploads/" + relativePath));
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

	/** Owner/admin: turns the loyalty points program on/off (PRO/MAX only) and sets how many points
	 * a client can bank before they have to redeem something to keep earning more. */
	@PatchMapping("/loyalty-rewards")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateLoyaltyRewards(@Valid @RequestBody LoyaltyRewardsSettingsRequest request) {
		return TenantResponse.from(tenantService.updateLoyaltyRewardsSettings(TenantContext.getTenantId(),
				request.enabled(), request.pointsCap()));
	}

	/** Owner/admin: turns the staff-commissions report on/off (PRO/MAX only). */
	@PatchMapping("/commissions")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateCommissions(@Valid @RequestBody CommissionsSettingsRequest request) {
		return TenantResponse
				.from(tenantService.updateCommissionsEnabled(TenantContext.getTenantId(), request.enabled()));
	}

	/** Owner/admin: turns public client reviews on/off (PRO/MAX only). */
	@PatchMapping("/reviews")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateReviews(@Valid @RequestBody ReviewsSettingsRequest request) {
		return TenantResponse.from(tenantService.updateReviewsEnabled(TenantContext.getTenantId(), request.enabled()));
	}

	/** Owner/admin: the tenant's own custom "feliz cumpleaños" message (MAX only) — BirthdayEmailScheduler
	 * sends it automatically to a client on their birthday, {@code {nombre}} substituted. Clearing it
	 * (null/blank) is allowed on any plan. */
	@PatchMapping("/birthday-message")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateBirthdayMessage(@Valid @RequestBody BirthdayMessageUpdateRequest request) {
		return TenantResponse
				.from(tenantService.updateBirthdayMessageTemplate(TenantContext.getTenantId(), request.message()));
	}

	/** Owner/admin: la comisión real (%) que Mercado Pago le cobra a este tenant — cuando está
	 * cargada, el checkout de la seña le suma ese % al cliente en vez de que el tenant absorba el
	 * costo (ver Tenant.mercadoPagoFeePercent). Null apaga el recargo. */
	@PatchMapping("/mercadopago-fee")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateMercadoPagoFee(@Valid @RequestBody MercadoPagoFeeUpdateRequest request) {
		return TenantResponse
				.from(tenantService.updateMercadoPagoFeePercent(TenantContext.getTenantId(), request.feePercent()));
	}

	/**
	 * Owner/admin: cuántos meses de historial de turnos se conservan (1–12) antes de que
	 * AppointmentRetentionScheduler los borre automáticamente. Ver
	 * AppointmentController#purgeHistory para el borrado manual inmediato (ventanas tipo "última
	 * hora").
	 */
	@PatchMapping("/history-retention")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateHistoryRetention(@Valid @RequestBody HistoryRetentionUpdateRequest request) {
		return TenantResponse.from(tenantService.updateHistoryRetentionMonths(TenantContext.getTenantId(),
				request.historyRetentionMonths()));
	}

	/**
	 * Owner/admin: how long (10–180 min) a PENDING deposit has before
	 * PendingDepositExpirationScheduler auto-cancels the appointment. Only has an effect for
	 * tenants with MercadoPago enabled in their plan — a tenant that only takes deposits via bank
	 * transfer alias is never touched by that scheduler regardless of this value (see its Javadoc).
	 */
	@PatchMapping("/deposit-expiration")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public TenantResponse updateDepositExpiration(@Valid @RequestBody DepositExpirationUpdateRequest request) {
		return TenantResponse.from(tenantService.updateDepositExpirationMinutes(TenantContext.getTenantId(),
				request.depositExpirationMinutes()));
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

	/** Owner-only: whether this tenant currently has its own MercadoPago account connected — lets
	 * the panel show "Conectar" or "Desconectar" without guessing from other state. */
	@GetMapping("/mercadopago/status")
	@PreAuthorize("hasRole('OWNER')")
	public MercadoPagoConnectionStatusResponse mercadoPagoStatus() {
		return new MercadoPagoConnectionStatusResponse(
				mercadoPagoAccountService.isConnected(TenantContext.getTenantId()));
	}

	/**
	 * Owner-only: unlinks this tenant's own MercadoPago account — checkouts/subscriptions fall back
	 * to the shared platform account again, same as before ever connecting. Doesn't revoke the
	 * OAuth grant on MercadoPago's own side (no API for that); see
	 * MercadoPagoAccountService#disconnect.
	 */
	@DeleteMapping("/mercadopago/connect")
	@PreAuthorize("hasRole('OWNER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnectMercadoPago() {
		mercadoPagoAccountService.disconnect(TenantContext.getTenantId());
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
