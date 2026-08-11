package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.tenant.PlanTier;

public class ServiceLimitExceededException extends BadRequestException {

	public ServiceLimitExceededException(PlanTier tier) {
		super("Plan " + tier + " allows at most " + tier.getMaxServices() + " services");
	}
}
