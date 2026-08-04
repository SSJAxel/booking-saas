package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.BranchHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record BranchHoursResponse(UUID id, UUID branchId, DayOfWeek dayOfWeek, LocalTime startTime,
		LocalTime endTime) {

	public static BranchHoursResponse from(BranchHours hours) {
		return new BranchHoursResponse(hours.getId(), hours.getBranchId(), hours.getDayOfWeek(), hours.getStartTime(),
				hours.getEndTime());
	}
}
