package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.Tenant;

/** Only what the public site map is allowed to list — name and slug, nothing that isn't already
 * visible on the tenant's own public booking page. */
public record PublicTenantDirectoryEntry(String name, String slug) {

	public static PublicTenantDirectoryEntry from(Tenant tenant) {
		return new PublicTenantDirectoryEntry(tenant.getName(), tenant.getSlug());
	}
}
