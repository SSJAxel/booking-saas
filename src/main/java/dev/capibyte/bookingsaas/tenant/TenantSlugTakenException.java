package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.ConflictException;

public class TenantSlugTakenException extends ConflictException {

	public TenantSlugTakenException(String slug) {
		super("Tenant slug '" + slug + "' is already taken");
	}
}
