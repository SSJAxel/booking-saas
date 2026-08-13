package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.BranchHours;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekly open window for a branch, e.g. "Monday 09:00-18:00" — the raw schedule data, so the
 * public site can compute "is it open right now" / "when does it open next" itself. */
public record PublicBranchHoursResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

	public static PublicBranchHoursResponse from(BranchHours hours) {
		return new PublicBranchHoursResponse(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
	}
}
