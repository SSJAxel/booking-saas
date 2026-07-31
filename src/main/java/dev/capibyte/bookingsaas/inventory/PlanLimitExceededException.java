package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.tenant.PlanTier;

public class PlanLimitExceededException extends BadRequestException {

	public PlanLimitExceededException(PlanTier tier) {
		super("Plan " + tier + " allows at most " + tier.getMaxProducts() + " active products");
	}
}
