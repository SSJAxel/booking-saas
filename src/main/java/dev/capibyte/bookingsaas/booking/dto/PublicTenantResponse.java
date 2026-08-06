package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Tenant;

/** Only what a public site is allowed to show — no status, plan, or internal id.
 * {@code transferAlias} is null when the tenant hasn't set one yet — the client-facing UI falls
 * back to a generic "contactá al negocio" message in that case (see BookingPage.jsx). */
public record PublicTenantResponse(String name, String slug, String logoUrl, String accentColor, String tagline,
		String transferAlias) {

	public static PublicTenantResponse from(Tenant tenant) {
		return new PublicTenantResponse(tenant.getName(), tenant.getSlug(), tenant.getLogoUrl(),
				tenant.getAccentColor(), tenant.getTagline(), tenant.getTransferAlias());
	}
}
