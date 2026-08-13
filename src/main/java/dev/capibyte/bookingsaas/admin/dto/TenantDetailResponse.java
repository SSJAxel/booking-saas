package dev.capibyte.bookingsaas.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/** Read-only cross-section of a tenant's own data, for the super-admin to answer support
 * questions ("how many professionals does this tenant have?") without impersonating its owner's
 * login. */
public record TenantDetailResponse(
		List<BranchInfo> branches,
		List<ProfessionalInfo> professionals,
		List<ServiceInfo> services) {

	public record BranchInfo(String name, String address, boolean active) {
	}

	public record ProfessionalInfo(String displayName, boolean active) {
	}

	public record ServiceInfo(String name, BigDecimal price, boolean active) {
	}
}
