package dev.capibyte.bookingsaas.tenant;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The single {@code platform_settings} row (see V30 migration, {@code CHECK (id = 1)}) — plain
 * {@code JdbcTemplate} rather than a full JPA {@code @Entity}, same reasoning as
 * {@code PlatformAdminRepository}: a one-row, two-column table doesn't need the entity machinery.
 */
@Repository
public class PlatformSettingsRepository {

	private final JdbcTemplate jdbcTemplate;

	public PlatformSettingsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public BigDecimal getReferenceBlueRate() {
		return jdbcTemplate.queryForObject("SELECT reference_blue_rate FROM platform_settings WHERE id = 1",
				BigDecimal.class);
	}

	/** Includes {@code updatedAt} (unlike {@link #getReferenceBlueRate}) — for surfacing "cuándo
	 * se actualizó por última vez" in the admin manual. */
	public Reference getReference() {
		return jdbcTemplate.queryForObject(
				"SELECT reference_blue_rate, updated_at FROM platform_settings WHERE id = 1",
				(rs, rowNum) -> new Reference(rs.getBigDecimal("reference_blue_rate"),
						rs.getTimestamp("updated_at").toInstant()));
	}

	public record Reference(BigDecimal referenceBlueRate, Instant updatedAt) {
	}

	public void updateReferenceBlueRate(BigDecimal rate) {
		jdbcTemplate.update("UPDATE platform_settings SET reference_blue_rate = ?, updated_at = now() WHERE id = 1",
				rate);
	}
}
