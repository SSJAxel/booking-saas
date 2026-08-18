package dev.capibyte.bookingsaas.booking;

import java.time.Instant;
import java.util.UUID;

/** One leg of a multi-service booking — see {@link AppointmentService#bookGroup}. */
public record BookGroupItem(UUID professionalId, UUID serviceId, Instant startTime) {
}
