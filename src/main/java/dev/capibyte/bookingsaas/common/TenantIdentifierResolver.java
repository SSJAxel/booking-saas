package dev.capibyte.bookingsaas.common;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hibernate binds a tenant identifier to every {@code Session}/{@code EntityManager} it opens —
 * not just ones that end up touching a {@code @TenantId} entity. That includes sessions opened
 * for non-tenant-scoped entities (e.g. {@code Tenant} itself, looked up by slug before we know
 * who's logging in) and the throwaway session Spring Data opens at startup to check for named
 * queries. So this can't require a context to already be set — it falls back to a sentinel
 * that resolves to "no such tenant", which is safe: any accidental query against a real
 * tenant-scoped table returns zero rows instead of leaking data.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

	private static final Logger log = LoggerFactory.getLogger(TenantIdentifierResolver.class);

	public static final UUID NO_TENANT = new UUID(0L, 0L);

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		UUID tenantId = TenantContext.getTenantId();
		if (tenantId == null) {
			log.trace("No tenant in context, falling back to the no-tenant sentinel");
			return NO_TENANT;
		}
		return tenantId;
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}
}
