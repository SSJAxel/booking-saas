package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(UUID id, UUID branchId, UUID professionalId, UUID serviceId, UUID clientId,
		Instant startTime, Instant endTime, AppointmentStatus status, PaymentStatus paymentStatus, String notes) {

	public static AppointmentResponse from(Appointment appointment) {
		return new AppointmentResponse(appointment.getId(), appointment.getBranchId(), appointment.getProfessionalId(),
				appointment.getServiceId(), appointment.getClientId(), appointment.getStartTime(),
				appointment.getEndTime(), appointment.getStatus(), appointment.getPaymentStatus(),
				appointment.getNotes());
	}
}
