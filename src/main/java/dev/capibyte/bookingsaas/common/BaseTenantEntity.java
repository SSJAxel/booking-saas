package dev.capibyte.bookingsaas.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Every tenant-scoped entity extends this. Hibernate appends "tenant_id = :current"
 * to every query and stamps it on insert automatically, based on {@link TenantContext}
 * via {@link TenantIdentifierResolver} — no repository is trusted to filter manually.
 */
@MappedSuperclass
@Getter
public abstract class BaseTenantEntity extends BaseEntity {

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
}
