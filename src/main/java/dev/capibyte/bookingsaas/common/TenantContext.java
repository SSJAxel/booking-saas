package dev.capibyte.bookingsaas.common;

import java.util.UUID;

/**
 * Holds the tenant resolved for the current request thread. Must be set before any
 * tenant-scoped repository call and cleared in a finally block — threads are reused
 * (pooled/virtual), so a leaked value here would leak data across tenants.
 */
public final class TenantContext {

	private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void setTenantId(UUID tenantId) {
		CURRENT.set(tenantId);
	}

	public static UUID getTenantId() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}
}
