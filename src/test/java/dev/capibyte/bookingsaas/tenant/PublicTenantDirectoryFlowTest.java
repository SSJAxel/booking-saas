package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** The public sitemap lists every ACTIVE tenant's booking page (see SecurityConfig's
 * /api/public-directory/** entry and PublicTenantDirectoryController) — this only checks
 * ACTIVE/PENDING_APPROVAL visibility, mirroring TenantApprovalFlowTest's gate on the booking site
 * itself. */
class PublicTenantDirectoryFlowTest extends IntegrationTestBase {

	@Test
	void activeTenantAppearsInDirectory() {
		RegisteredTenant tenant = registerTenant();

		ResponseEntity<Map[]> response = restTemplate.getForEntity("/api/public-directory/tenants", Map[].class);

		assertThat(response.getBody()).isNotNull();
		List<Map> slugs = List.of(response.getBody());
		assertThat(slugs).anyMatch(entry -> tenant.slug().equals(entry.get("slug")));
	}

	@Test
	void pendingTenantDoesNotAppearInDirectory() {
		String slug = "pending-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		Map<String, Object> request = Map.of(
				"tenantName", "Pending Test " + slug,
				"tenantSlug", slug,
				"ownerEmail", email,
				"ownerPassword", "supersecret123");
		restTemplate.postForEntity("/api/auth/register", request, Map.class);
		jdbcTemplate.update("UPDATE app_users SET email_verified = true WHERE email = ?", email);
		// Deliberately not approved — status stays PENDING_APPROVAL (registerTenant() is the helper
		// that fast-forwards to ACTIVE; skipped here on purpose).

		ResponseEntity<Map[]> response = restTemplate.getForEntity("/api/public-directory/tenants", Map[].class);

		assertThat(response.getBody()).isNotNull();
		List<Map> slugs = List.of(response.getBody());
		assertThat(slugs).noneMatch(entry -> slug.equals(entry.get("slug")));
	}
}
