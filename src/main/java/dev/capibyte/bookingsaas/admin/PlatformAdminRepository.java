package dev.capibyte.bookingsaas.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

	/** Backs the usage-ranking table's "Sucursales" column. Same "tenants with zero are absent"
	 * convention as {@link #countProfessionalsByTenant}. */
	public Map<UUID, Long> countBranchesByTenant() {
		String sql = "SELECT tenant_id, COUNT(*) AS branch_count FROM branches GROUP BY tenant_id";
		return jdbcTemplate.query(sql, rs -> {
			Map<UUID, Long> result = new java.util.HashMap<>();
			while (rs.next()) {
				result.put(UUID.fromString(rs.getString("tenant_id")), rs.getLong("branch_count"));
			}
			return result;
		});
	}

	/** Backs the usage-ranking table's "Servicios" column. */
	public Map<UUID, Long> countServicesByTenant() {
		String sql = "SELECT tenant_id, COUNT(*) AS service_count FROM service_offerings GROUP BY tenant_id";
		return jdbcTemplate.query(sql, rs -> {
			Map<UUID, Long> result = new java.util.HashMap<>();
			while (rs.next()) {
				result.put(UUID.fromString(rs.getString("tenant_id")), rs.getLong("service_count"));
			}
			return result;
		});
	}

	/** Backs the usage-ranking table's "Stock" column — total units across every product, not a
	 * product count (see Product's Javadoc: {@code stock} is a per-product unit quantity). */
	public Map<UUID, Long> sumStockByTenant() {
		String sql = "SELECT tenant_id, SUM(stock) AS stock_units FROM products GROUP BY tenant_id";
		return jdbcTemplate.query(sql, rs -> {
			Map<UUID, Long> result = new java.util.HashMap<>();
			while (rs.next()) {
				result.put(UUID.fromString(rs.getString("tenant_id")), rs.getLong("stock_units"));
			}
			return result;
		});
	}

	public List<AdminSupportReportRow> findAllSupportReports() {
		String sql = """
				SELECT sr.id, sr.tenant_id, t.name AS tenant_name, t.slug AS tenant_slug,
				       au.email AS submitter_email, sr.type, sr.priority, sr.message, sr.image_path,
				       sr.resolved, sr.created_at
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
				rs.getString("priority"),
				rs.getString("message"),
				rs.getString("image_path") != null,
				rs.getBoolean("resolved"),
				rs.getTimestamp("created_at").toInstant()));
	}

	public void updatePriority(UUID id, String priority) {
		jdbcTemplate.update("UPDATE support_reports SET priority = ? WHERE id = ?", priority, id);
	}

	/** Billing report rows, one per subscription attempt in range — matches Subscription's own
	 * "one row per attempt, not per tenant" model (see V8__subscriptions.sql). {@code from}/{@code
	 * to} bound {@code created_at}'s date, inclusive on both ends. */
	public List<BillingRow> findSubscriptionPayments(LocalDate from, LocalDate to) {
		String sql = """
				SELECT s.tenant_id, t.name AS tenant_name, s.plan_tier, s.amount, s.status, s.created_at
				FROM subscriptions s
				JOIN tenants t ON t.id = s.tenant_id
				WHERE s.created_at >= ? AND s.created_at < ?
				ORDER BY s.created_at DESC
				""";
		return jdbcTemplate.query(sql, (rs, rowNum) -> new BillingRow(
				UUID.fromString(rs.getString("tenant_id")),
				rs.getString("tenant_name"),
				rs.getString("plan_tier"),
				rs.getBigDecimal("amount"),
				rs.getString("status"),
				rs.getTimestamp("created_at").toInstant()),
				java.sql.Timestamp.from(from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()),
				java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
	}

	/** Appointment count + revenue per tenant over the given window, COMPLETED only — same revenue
	 * definition as ReportService's per-tenant figure, computed in SQL here (rather than looping
	 * TenantContext per tenant) since this needs every tenant in one round trip, consistent with
	 * this repository's other cross-tenant aggregations. Tenants with zero completed appointments
	 * in the window are absent from the result. */
	public List<UsageRow> findTenantUsage(Instant from, Instant to) {
		String sql = """
				SELECT a.tenant_id, COUNT(*) AS appointment_count, SUM(so.price) AS revenue
				FROM appointments a
				JOIN service_offerings so ON so.id = a.service_id
				WHERE a.status = 'COMPLETED' AND a.start_time >= ? AND a.start_time < ?
				GROUP BY a.tenant_id
				""";
		return jdbcTemplate.query(sql, (rs, rowNum) -> new UsageRow(
				UUID.fromString(rs.getString("tenant_id")),
				rs.getLong("appointment_count"),
				rs.getBigDecimal("revenue")),
				java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
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
			String submitterEmail, String type, String priority, String message, boolean hasImage,
			boolean resolved, Instant createdAt) {
	}

	public record ImageRef(String imagePath, String contentType) {
	}

	public record BillingRow(UUID tenantId, String tenantName, String planTier, BigDecimal amount,
			String status, Instant createdAt) {
	}

	public record UsageRow(UUID tenantId, long appointmentCount, BigDecimal revenue) {
	}
}
