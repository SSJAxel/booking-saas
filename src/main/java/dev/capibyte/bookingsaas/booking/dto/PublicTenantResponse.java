package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Tenant;

/** Only what a public site is allowed to show — no status, plan, or internal id.
 * {@code transferAlias} is null when the tenant hasn't set one yet — the client-facing UI falls
 * back to a generic "contactá al negocio" message in that case (see BookingPage.jsx).
 * {@code timezone} lets the public page compute "abierto/cerrado ahora" against the business's own
 * clock instead of assuming it matches the visitor's browser. */
public record PublicTenantResponse(String name, String slug, String logoUrl, String bannerUrl, String accentColor,
		String tagline, String transferAlias, String timezone, String instagramUrl, String facebookUrl,
		String instagramFeedUrl) {

	public static PublicTenantResponse from(Tenant tenant) {
		return new PublicTenantResponse(tenant.getName(), tenant.getSlug(), tenant.getLogoUrl(),
				tenant.getBannerUrl(), tenant.getAccentColor(), tenant.getTagline(), tenant.getTransferAlias(),
				tenant.getTimezone(), tenant.getInstagramUrl(), tenant.getFacebookUrl(),
				tenant.getInstagramFeedUrl());
	}
}
