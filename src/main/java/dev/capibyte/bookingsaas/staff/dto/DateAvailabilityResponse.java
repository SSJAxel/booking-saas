package dev.capibyte.bookingsaas.staff.dto;

import dev.capibyte.bookingsaas.staff.DateAvailability;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DateAvailabilityResponse(UUID id, UUID professionalId, LocalDate date, LocalTime startTime,
		LocalTime endTime) {

	public static DateAvailabilityResponse from(DateAvailability availability) {
		return new DateAvailabilityResponse(availability.getId(), availability.getProfessionalId(),
				availability.getDate(), availability.getStartTime(), availability.getEndTime());
	}
}
