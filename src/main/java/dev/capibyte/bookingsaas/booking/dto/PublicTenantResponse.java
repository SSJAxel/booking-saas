package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Tenant;

/** Only what a public site is allowed to show — no status, plan, or internal id. */
public record PublicTenantResponse(String name, String slug, String logoUrl, String accentColor, String tagline) {

	public static PublicTenantResponse from(Tenant tenant) {
		return new PublicTenantResponse(tenant.getName(), tenant.getSlug(), tenant.getLogoUrl(),
				tenant.getAccentColor(), tenant.getTagline());
	}
}
