package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Tenant;
import java.math.BigDecimal;

/** Only what a public site is allowed to show — no status, plan name, or internal id.
 * {@code transferAlias} is null when the tenant hasn't set one yet — the client-facing UI falls
 * back to a generic "contactá al negocio" message in that case (see BookingPage.jsx).
 * {@code timezone} lets the public page compute "abierto/cerrado ahora" against the business's own
 * clock instead of assuming it matches the visitor's browser. {@code mercadoPagoEnabled} is the one
 * plan-derived bit that does need to reach the public page: without it, ReservationModal has no way
 * to know whether to offer a "Pagar con Mercado Pago" button for a pending deposit versus falling
 * back to {@code transferAlias} — same reasoning as exposing transferAlias itself, not the plan tier
 * that gates it. {@code mercadoPagoFeePercent} lets ReservationModal show the client the real amount
 * they're about to pay (seña + comisión) on the button BEFORE they click it — the actual charge is
 * always recomputed server-side by {@code PaymentService#createCheckout}, this is only a preview,
 * same "never trust the client, this is just for display" pattern as the combo-price preview. */
public record PublicTenantResponse(String name, String slug, String logoUrl, String bannerUrl, String accentColor,
		String tagline, String transferAlias, String timezone, String instagramUrl, String facebookUrl,
		String instagramFeedUrl, boolean mercadoPagoEnabled, BigDecimal mercadoPagoFeePercent) {

	public static PublicTenantResponse from(Tenant tenant) {
		return new PublicTenantResponse(tenant.getName(), tenant.getSlug(), tenant.getLogoUrl(),
				tenant.getBannerUrl(), tenant.getAccentColor(), tenant.getTagline(), tenant.getTransferAlias(),
				tenant.getTimezone(), tenant.getInstagramUrl(), tenant.getFacebookUrl(),
				tenant.getInstagramFeedUrl(), tenant.getPlanTier().isMercadoPagoEnabled(),
				tenant.getMercadoPagoFeePercent());
	}
}
