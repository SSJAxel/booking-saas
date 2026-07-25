package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant itself is NOT tenant-scoped (there's no outer scope to filter it by) —
 * it's the root every {@link dev.capibyte.bookingsaas.common.BaseTenantEntity} hangs off of.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private String timezone = "UTC";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenantStatus status = TenantStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
