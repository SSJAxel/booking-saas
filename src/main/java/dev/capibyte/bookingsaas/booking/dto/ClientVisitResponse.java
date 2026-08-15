package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;

/** One row of a client's visit history — GET /api/clients/{id}/history, most recent first. */
public record ClientVisitResponse(
		UUID appointmentId,
		Instant startTime,
		String serviceName,
		String professionalName,
		AppointmentStatus status) {
}
