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

	/** Backs the "Profesionales" column in the admin tenants table — context for the founder's
	 * approval/pricing call (billing is per-employee), purely informational here. Tenants with zero
	 * professionals are absent from the map. */
	public Map<UUID, Long> countProfessionalsByTenant() {
		String sql = "SELECT tenant_id, COUNT(*) AS professional_count FROM professionals GROUP BY tenant_id";
		return jdbcTemplate.query(sql, rs -> {
			Map<UUID, Long> result = new java.util.HashMap<>();
			while (rs.next()) {
				result.put(UUID.fromString(rs.getString("tenant_id")), rs.getLong("professional_count"));
			}
			return result;
		});
	}

	/** Single-tenant counterpart to {@link #countProfessionalsByTenant}, same "used after a
	 * single-tenant write" reasoning as {@link #findLatestSubscriptionStatus}. */
	public long countProfessionalsForTenant(UUID tenantId) {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM professionals WHERE tenant_id = ?", Long.class,
				tenantId);
		return count == null ? 0 : count;
	}

	public List<AdminSupportReportRow> findAllSupportReports() {
		String sql = """
				SELECT sr.id, sr.tenant_id, t.name AS tenant_name, t.slug AS tenant_slug,
				       au.email AS submitter_email, sr.type, sr.message, sr.image_path, sr.resolved, sr.created_at
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
				rs.getString("type"),
				rs.getString("message"),
				rs.getString("image_path") != null,
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

	/** Permanent delete — for a bug "report" that turns out not to be a real bug, so it doesn't
	 * clutter the (otherwise permanent) resolved history. Doesn't clean up the uploaded screenshot
	 * file, if any; an orphaned file on disk is a minor cleanup issue, not a functional one. */
	public void deleteSupportReport(UUID id) {
		jdbcTemplate.update("DELETE FROM support_reports WHERE id = ?", id);
	}

	public record AdminSupportReportRow(UUID id, UUID tenantId, String tenantName, String tenantSlug,
			String submitterEmail, String type, String message, boolean hasImage, boolean resolved,
			Instant createdAt) {
	}

	public record ImageRef(String imagePath, String contentType) {
	}
}
