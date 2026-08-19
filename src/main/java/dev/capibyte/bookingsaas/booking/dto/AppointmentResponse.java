package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import dev.capibyte.bookingsaas.booking.Client;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(UUID id, UUID branchId, UUID professionalId, UUID serviceId, UUID clientId,
		String clientName, String clientEmail, String clientPhone, Instant startTime, Instant endTime,
		AppointmentStatus status, PaymentStatus paymentStatus, String notes, boolean overtime, UUID bookingGroupId,
		BigDecimal depositAmountOverride) {

	public static AppointmentResponse from(Appointment appointment, Client client) {
		return new AppointmentResponse(appointment.getId(), appointment.getBranchId(), appointment.getProfessionalId(),
				appointment.getServiceId(), appointment.getClientId(), client.getName(), client.getEmail(),
				client.getPhone(), appointment.getStartTime(), appointment.getEndTime(), appointment.getStatus(),
				appointment.getPaymentStatus(), appointment.getNotes(), appointment.isOvertime(),
				appointment.getBookingGroupId(), appointment.getDepositAmountOverride());
	}
}
