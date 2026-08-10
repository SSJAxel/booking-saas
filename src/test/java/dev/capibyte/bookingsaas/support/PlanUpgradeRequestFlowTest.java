package dev.capibyte.bookingsaas.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "Mejorar plan" (TenantPage) → alerts the founder instead of an automated checkout, lands in the
 * same super-admin inbox as bug reports (distinguished by {@code type}), and follows the same
 * pendiente → resuelto (permanent history) → delete lifecycle agreed on for that inbox.
 */
class PlanUpgradeRequestFlowTest extends IntegrationTestBase {

	@Test
	void planUpgradeRequestReachesTheAdminInboxAsItsOwnTypeAndFollowsTheResolveDeleteLifecycle() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Void> submitted = restTemplate.exchange("/api/support-reports/plan-upgrade", HttpMethod.POST,
				new HttpEntity<>(Map.of("note", "Necesito más profesionales, ¿pasamos a PRO?"), headers), Void.class);
		assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);

		ResponseEntity<Map[]> listed = restTemplate.exchange("/api/admin/support-reports", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		Map report = findByTenantSlug(listed.getBody(), tenant.slug());
		assertThat(report.get("type")).isEqualTo("PLAN_UPGRADE");
		assertThat(report.get("message")).isEqualTo("Necesito más profesionales, ¿pasamos a PRO?");
		assertThat(report.get("hasImage")).isEqualTo(false);
		assertThat(report.get("resolved")).isEqualTo(false);
		String reportId = (String) report.get("id");

		// Resolve — moves into the permanent "historial".
		restTemplate.exchange("/api/admin/support-reports/" + reportId + "/resolved", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("resolved", true), adminHeaders), Void.class);
		ResponseEntity<Map[]> afterResolve = restTemplate.exchange("/api/admin/support-reports", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		assertThat(findByTenantSlug(afterResolve.getBody(), tenant.slug()).get("resolved")).isEqualTo(true);

		// Delete — for a false positive; must actually be gone, not just hidden.
		restTemplate.exchange("/api/admin/support-reports/" + reportId, HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		ResponseEntity<Map[]> afterDelete = restTemplate.exchange("/api/admin/support-reports", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		assertThat(afterDelete.getBody()).noneMatch(r -> tenant.slug().equals(r.get("tenantSlug")));
	}

	private Map findByTenantSlug(Map[] reports, String slug) {
		for (Map report : reports) {
			if (slug.equals(report.get("tenantSlug"))) return report;
		}
		throw new AssertionError("No report found for tenant " + slug);
	}

	/** Marks the tenant's owner as a platform admin directly in the DB (same "bypass the external
	 * dependency, not the behavior under test" shortcut IntegrationTestBase already uses for email
	 * verification) and logs in again — the JWT's platformAdmin claim is only set at login time. */
	private HttpHeaders promoteToPlatformAdminAndLogin(RegisteredTenant tenant) {
		String email = tenant.slug() + "@example.com";
		jdbcTemplate.update("UPDATE app_users SET platform_admin = true WHERE email = ?", email);
		Map<String, Object> loginRequest = Map.of("tenantSlug", tenant.slug(), "email", email, "password",
				"supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		return authHeaders((String) loginResponse.getBody().get("token"));
	}
}
