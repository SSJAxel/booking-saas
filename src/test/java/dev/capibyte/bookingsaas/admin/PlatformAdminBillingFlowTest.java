package dev.capibyte.bookingsaas.admin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers the 7 super-admin features added for billing/reporting: custom price, MRR, usage
 * ranking, tenant detail (read-only, no impersonation), billing CSV export, and support-report
 * priority. Search/filter (tenant list) is frontend-only, so it has no backend test here.
 */
class PlatformAdminBillingFlowTest extends IntegrationTestBase {

	@Test
	void customPriceOverridesEffectivePriceAndFeedsIntoMrr() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);
		String tenantId = findTenantId(adminHeaders, tenant.slug());

		// Give it a real plan first — TRIAL is free (zero), which would make the MRR assertion
		// below trivially true even if the override wasn't actually being applied.
		restTemplate.exchange("/api/admin/tenants/" + tenantId + "/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "PRO"), adminHeaders), Map.class);

		ResponseEntity<Map> updated = restTemplate.exchange("/api/admin/tenants/" + tenantId + "/custom-price",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("customMonthlyPrice", 9999.50), adminHeaders), Map.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new BigDecimal(updated.getBody().get("customMonthlyPrice").toString()))
				.isEqualByComparingTo("9999.50");
		assertThat(new BigDecimal(updated.getBody().get("effectiveMonthlyPrice").toString()))
				.isEqualByComparingTo("9999.50");

		ResponseEntity<Map> mrr = restTemplate.exchange("/api/admin/mrr", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map.class);
		assertThat(mrr.getStatusCode()).isEqualTo(HttpStatus.OK);
		BigDecimal totalMrr = new BigDecimal(mrr.getBody().get("totalMrr").toString());
		// >= not == : other tests running against the same DB may also contribute ACTIVE tenants.
		assertThat(totalMrr).isGreaterThanOrEqualTo(new BigDecimal("9999.50"));

		// Clearing the override falls back to the plan's list price, not zero/null.
		ResponseEntity<Map> cleared = restTemplate.exchange("/api/admin/tenants/" + tenantId + "/custom-price",
				HttpMethod.PATCH, new HttpEntity<>(java.util.Collections.singletonMap("customMonthlyPrice", null),
						adminHeaders),
				Map.class);
		assertThat(cleared.getBody().get("customMonthlyPrice")).isNull();
		// PRO's current list price per plan_pricing (V30 seed) — see PlanTier's Javadoc for why
		// this is no longer PlanTier.PRO.getMonthlyPrice() (that's null now).
		assertThat(new BigDecimal(cleared.getBody().get("effectiveMonthlyPrice").toString()))
				.isEqualByComparingTo("50000.00");
	}

	@Test
	void updatingPlanByHandMarksItAsManuallySetInTheSummary() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);
		String tenantId = findTenantId(adminHeaders, tenant.slug());

		ResponseEntity<Map> updated = restTemplate.exchange("/api/admin/tenants/" + tenantId + "/plan",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("planTier", "MAX"), adminHeaders), Map.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().get("planManuallySet")).isEqualTo(true);
	}

	@Test
	void tenantDetailExposesOwnDataWithoutImpersonation() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders ownerHeaders = authHeaders(tenant.token());
		restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Sucursal Centro"), ownerHeaders), Map.class);

		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);
		String tenantId = findTenantId(adminHeaders, tenant.slug());

		ResponseEntity<Map> detail = restTemplate.exchange("/api/admin/tenants/" + tenantId + "/detail",
				HttpMethod.GET, new HttpEntity<>(adminHeaders), Map.class);
		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		java.util.List<Map> branches = (java.util.List<Map>) detail.getBody().get("branches");
		assertThat(branches).extracting(b -> b.get("name")).containsExactly("Sucursal Centro");
	}

	@Test
	void usageRankingListsEveryTenantEvenWithZeroActivity() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);

		ResponseEntity<Map[]> usage = restTemplate.exchange("/api/admin/tenants/usage", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		assertThat(usage.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map row = findByTenantId(usage.getBody(), findTenantId(adminHeaders, tenant.slug()));
		assertThat(row.get("appointmentCount")).isEqualTo(0);
	}

	@Test
	void billingReportDownloadsAsCsv() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);

		String url = "/api/admin/billing-report?from=" + LocalDate.now().minusDays(30) + "&to=" + LocalDate.now();
		ResponseEntity<byte[]> report = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(adminHeaders),
				byte[].class);
		assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(report.getHeaders().getContentType().toString()).contains("text/csv");
		assertThat(new String(report.getBody())).startsWith("Tenant,Plan,Monto,Estado,Fecha");
	}

	@Test
	void supportReportPriorityDefaultsToMediaAndCanBeUpdated() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders ownerHeaders = authHeaders(tenant.token());
		restTemplate.exchange("/api/support-reports/plan-upgrade", HttpMethod.POST,
				new HttpEntity<>(Map.of("note", "quiero más profesionales"), ownerHeaders), Void.class);

		HttpHeaders adminHeaders = promoteToPlatformAdminAndLogin(tenant);
		ResponseEntity<Map[]> reports = restTemplate.exchange("/api/admin/support-reports", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		Map report = findByTenantSlug(reports.getBody(), tenant.slug());
		assertThat(report.get("priority")).isEqualTo("MEDIA");
		String reportId = (String) report.get("id");

		ResponseEntity<Void> updated = restTemplate.exchange("/api/admin/support-reports/" + reportId + "/priority",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("priority", "ALTA"), adminHeaders), Void.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<Map[]> afterUpdate = restTemplate.exchange("/api/admin/support-reports", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		assertThat(findByTenantSlug(afterUpdate.getBody(), tenant.slug()).get("priority")).isEqualTo("ALTA");
	}

	private String findTenantId(HttpHeaders adminHeaders, String slug) {
		ResponseEntity<Map[]> tenants = restTemplate.exchange("/api/admin/tenants", HttpMethod.GET,
				new HttpEntity<>(adminHeaders), Map[].class);
		for (Map tenant : tenants.getBody()) {
			if (slug.equals(tenant.get("slug"))) return (String) tenant.get("tenantId");
		}
		throw new AssertionError("No tenant found for slug " + slug);
	}

	private Map findByTenantId(Map[] rows, String tenantId) {
		for (Map row : rows) {
			if (tenantId.equals(row.get("tenantId"))) return row;
		}
		throw new AssertionError("No row found for tenant " + tenantId);
	}

	private Map findByTenantSlug(Map[] reports, String slug) {
		for (Map report : reports) {
			if (slug.equals(report.get("tenantSlug"))) return report;
		}
		throw new AssertionError("No report found for tenant " + slug);
	}

	/** Same shortcut PlanUpgradeRequestFlowTest/TenantApprovalFlowTest already use: promote
	 * directly in the DB, then log in again since the JWT's platformAdmin claim is only set at
	 * login time. */
	private HttpHeaders promoteToPlatformAdminAndLogin(RegisteredTenant tenant) {
		String email = tenant.slug() + "@example.com";
		jdbcTemplate.update("UPDATE app_users SET platform_admin = true WHERE email = ?", email);
		Map<String, Object> loginRequest = Map.of("tenantSlug", tenant.slug(), "email", email, "password",
				"supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		return authHeaders((String) loginResponse.getBody().get("token"));
	}
}
