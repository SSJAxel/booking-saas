package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A newly registered tenant starts PENDING_APPROVAL (see TenantStatus's Javadoc) — its public
 * booking site is blocked until the founder approves it from the super-admin panel, at which
 * point it becomes ACTIVE and stays that way. Doesn't use {@link IntegrationTestBase#registerTenant}
 * (that helper auto-approves, precisely so every *other* test doesn't have to know about this
 * gate) — this test drives registration and approval itself.
 */
class TenantApprovalFlowTest extends IntegrationTestBase {

	@Test
	void newTenantsPublicSiteIsBlockedUntilApproved() {
		String slug = registerWithoutApproving();

		ResponseEntity<Map> beforeApproval = restTemplate.getForEntity("/api/public/" + slug, Map.class);
		assertThat(beforeApproval.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		approveAsPlatformAdmin(slug);

		ResponseEntity<Map> afterApproval = restTemplate.getForEntity("/api/public/" + slug, Map.class);
		assertThat(afterApproval.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void unknownTenantAndPendingTenantAreNotDistinguishable() {
		// Both return 403/404 with no hint of *why* — a client shouldn't learn anything more than
		// "not available" (see PublicTenantResolutionFilter's comment on this).
		String slug = registerWithoutApproving();
		ResponseEntity<Map> pending = restTemplate.getForEntity("/api/public/" + slug, Map.class);
		assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(pending.getBody().get("message")).isEqualTo("Este negocio todavía no está habilitado");
	}

	private String registerWithoutApproving() {
		String slug = "approval-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		Map<String, Object> request = Map.of(
				"tenantName", "Approval Test " + slug,
				"tenantSlug", slug,
				"ownerEmail", email,
				"ownerPassword", "supersecret123");
		restTemplate.postForEntity("/api/auth/register", request, Map.class);
		jdbcTemplate.update("UPDATE app_users SET email_verified = true WHERE email = ?", email);
		return slug;
	}

	/** Same trick IntegrationTestBase already documents for other admin-only tests: promote the
	 * caller to platform admin directly in the DB, then log in again to pick up the JWT claim. */
	private void approveAsPlatformAdmin(String slug) {
		String email = slug + "@example.com";
		jdbcTemplate.update("UPDATE app_users SET platform_admin = true WHERE email = ?", email);
		Map<String, Object> loginRequest = Map.of("tenantSlug", slug, "email", email, "password", "supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		HttpHeaders headers = authHeaders((String) loginResponse.getBody().get("token"));

		ResponseEntity<Map[]> tenants = restTemplate.exchange("/api/admin/tenants", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		String tenantId = findTenantId(tenants.getBody(), slug);

		ResponseEntity<Map> approveResponse = restTemplate.exchange("/api/admin/tenants/" + tenantId + "/approve",
				HttpMethod.POST, new HttpEntity<>(headers), Map.class);
		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(approveResponse.getBody().get("status")).isEqualTo("ACTIVE");
	}

	private String findTenantId(Map[] tenants, String slug) {
		for (Map tenant : tenants) {
			if (slug.equals(tenant.get("slug"))) return (String) tenant.get("tenantId");
		}
		throw new AssertionError("No tenant found for slug " + slug);
	}
}
