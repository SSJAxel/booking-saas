package dev.capibyte.bookingsaas.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cross-tenant reads via hand-written SQL, deliberately bypassing the Hibernate entity layer:
 * Hibernate's {@code @TenantId} discriminator multi-tenancy only rewrites HQL/Criteria-generated
 * SQL, never native SQL, and {@code TenantIdentifierResolver} doesn't support "no filter" or
 * changing tenant mid-session — so this is the only way to read across every tenant's rows in one
 * round trip without opening a transaction per tenant.
 */
@Repository
public class PlatformAdminRepository {

	private final JdbcTemplate jdbcTemplate;

	public PlatformAdminRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Latest subscription row per tenant (by created_at) — "the current one", same definition
	 * SubscriptionRepository uses per-tenant. Tenants that never subscribed are absent from the map. */
	public Map<UUID, String> findLatestSubscriptionStatusByTenant() {
		String sql = """
				SELECT DISTINCT ON (tenant_id) tenant_id, status
				FROM subscriptions
				ORDER BY tenant_id, created_at DESC
				""";
		return jdbcTemplate.query(sql, rs -> {
			Map<UUID, String> result = new java.util.HashMap<>();
			while (rs.next()) {
				result.put(UUID.fromString(rs.getString("tenant_id")), rs.getString("status"));
			}
			return result;
		});
	}

	/** Same "current" definition as {@link #findLatestSubscriptionStatusByTenant} — used after a
	 * single-tenant write so the response doesn't require re-running the full cross-tenant query. */
	public Optional<String> findLatestSubscriptionStatus(UUID tenantId) {
		String sql = "SELECT status FROM subscriptions WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1";
		List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("status"), tenantId);
		return rows.stream().findFirst();
	}

	public List<AdminSupportReportRow> findAllSupportReports() {
		String sql = """
				SELECT sr.id, sr.tenant_id, t.name AS tenant_name, t.slug AS tenant_slug,
				       au.email AS submitter_email, sr.message, sr.resolved, sr.created_at
				FROM support_reports sr
				JOIN tenants t ON t.id = sr.tenant_id
				JOIN app_users au ON au.id = sr.app_user_id
				ORDER BY sr.created_at DESC
				""";
		return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSupportReportRow(
				UUID.fromString(rs.getString("id")),
				UUID.fromString(rs.getString("tenant_id")),
				rs.getString("tenant_name"),
				rs.getString("tenant_slug"),
				rs.getString("submitter_email"),
				rs.getString("message"),
				rs.getBoolean("resolved"),
				rs.getTimestamp("created_at").toInstant()));
	}

	public Optional<ImageRef> findSupportReportImage(UUID id) {
		String sql = "SELECT image_path, image_content_type FROM support_reports WHERE id = ?";
		List<ImageRef> rows = jdbcTemplate.query(sql,
				(rs, rowNum) -> new ImageRef(rs.getString("image_path"), rs.getString("image_content_type")), id);
		return rows.stream().findFirst();
	}

	public void markResolved(UUID id, boolean resolved) {
		jdbcTemplate.update("UPDATE support_reports SET resolved = ? WHERE id = ?", resolved, id);
	}

	public record AdminSupportReportRow(UUID id, UUID tenantId, String tenantName, String tenantSlug,
			String submitterEmail, String message, boolean resolved, Instant createdAt) {
	}

	public record ImageRef(String imagePath, String contentType) {
	}
}
