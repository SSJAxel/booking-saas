package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BadRequestException;

public class BranchLimitExceededException extends BadRequestException {

	public BranchLimitExceededException(PlanTier tier) {
		super("Plan " + tier + " allows at most " + tier.getMaxBranches() + " branches");
	}
}
