package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantStatus;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, String timezone, TenantStatus status,
		PlanTier planTier, String logoUrl, String accentColor, String tagline, boolean whatsappEnabled,
		String contactEmail, String whatsappNumber, String transferAlias, int topClientsThreshold,
		int topClientsCount) {

	public static TenantResponse from(Tenant tenant) {
		return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getTimezone(),
				tenant.getStatus(), tenant.getPlanTier(), tenant.getLogoUrl(), tenant.getAccentColor(),
				tenant.getTagline(), tenant.isWhatsappEnabled(), tenant.getContactEmail(),
				tenant.getWhatsappNumber(), tenant.getTransferAlias(), tenant.getTopClientsThreshold(),
				tenant.getTopClientsCount());
	}
}
