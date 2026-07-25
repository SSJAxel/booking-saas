package dev.capibyte.bookingsaas.staff.dto;

import dev.capibyte.bookingsaas.staff.WeeklyAvailability;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record WeeklyAvailabilityResponse(UUID id, UUID professionalId, DayOfWeek dayOfWeek, LocalTime startTime,
		LocalTime endTime) {

	public static WeeklyAvailabilityResponse from(WeeklyAvailability availability) {
		return new WeeklyAvailabilityResponse(availability.getId(), availability.getProfessionalId(),
				availability.getDayOfWeek(), availability.getStartTime(), availability.getEndTime());
	}
}
