package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.tenant.PlanTier;

public class WeeklyAppointmentLimitExceededException extends BadRequestException {

	public WeeklyAppointmentLimitExceededException(PlanTier tier) {
		super("Plan " + tier + " allows at most " + tier.getMaxAppointmentsPerWeek()
				+ " appointments per calendar week");
	}
}
