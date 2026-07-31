package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.tenant.dto.PlanCatalogResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately not under /api/public/{tenantSlug}/** — PublicTenantResolutionFilter treats
 * anything there as a tenant slug and 404s if it doesn't resolve to one, and this isn't
 * tenant-scoped at all (it's the platform's own plan catalog, for a prospective signup to see
 * before they have a tenant). Public regardless — see SecurityConfig's PUBLIC_PATHS.
 */
@RestController
public class PlanCatalogController {

	@GetMapping("/api/plans")
	public List<PlanCatalogResponse> list() {
		return Arrays.stream(PlanTier.values()).map(PlanCatalogResponse::from).toList();
	}
}
