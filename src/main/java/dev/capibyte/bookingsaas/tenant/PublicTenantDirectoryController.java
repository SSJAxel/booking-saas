package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.tenant.dto.PublicTenantDirectoryEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately outside {@code /api/public/{tenantSlug}/**}: that prefix is claimed by
 * PublicTenantResolutionFilter, which treats the next path segment as a tenant slug to resolve —
 * a sibling listing endpoint would be swallowed as a lookup for a tenant literally named
 * "tenants". No auth (see SecurityConfig's PUBLIC_PATHS): this only ever lists what's already
 * reachable on each tenant's own public booking page.
 */
@RestController
@RequestMapping("/api/public-directory")
@RequiredArgsConstructor
public class PublicTenantDirectoryController {

	private final TenantService tenantService;

	@GetMapping("/tenants")
	public List<PublicTenantDirectoryEntry> tenants() {
		return tenantService.findAllActive().stream().map(PublicTenantDirectoryEntry::from).toList();
	}
}
