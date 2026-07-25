package dev.capibyte.bookingsaas.staff.dto;

import dev.capibyte.bookingsaas.staff.TimeOff;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeOffResponse(UUID id, UUID professionalId, LocalDate date, LocalTime startTime, LocalTime endTime,
		String reason) {

	public static TimeOffResponse from(TimeOff timeOff) {
		return new TimeOffResponse(timeOff.getId(), timeOff.getProfessionalId(), timeOff.getDate(),
				timeOff.getStartTime(), timeOff.getEndTime(), timeOff.getReason());
	}
}
