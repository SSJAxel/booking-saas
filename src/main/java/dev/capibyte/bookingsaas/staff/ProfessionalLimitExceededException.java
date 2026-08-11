package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.tenant.PlanTier;

public class ProfessionalLimitExceededException extends BadRequestException {

	public ProfessionalLimitExceededException(PlanTier tier) {
		super("Plan " + tier + " allows at most " + tier.getMaxProfessionals() + " professionals");
	}
}
