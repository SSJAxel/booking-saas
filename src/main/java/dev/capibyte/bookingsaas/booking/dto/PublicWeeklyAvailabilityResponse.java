package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.staff.WeeklyAvailability;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** One recurring weekly window a professional works, e.g. "Tuesday 09:00-18:00" — the raw
 * schedule, same shape as PublicBranchHoursResponse, for a "meet the team" hover/card to show
 * real hours instead of just the bio. */
public record PublicWeeklyAvailabilityResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

	public static PublicWeeklyAvailabilityResponse from(WeeklyAvailability availability) {
		return new PublicWeeklyAvailabilityResponse(availability.getDayOfWeek(), availability.getStartTime(),
				availability.getEndTime());
	}
}
