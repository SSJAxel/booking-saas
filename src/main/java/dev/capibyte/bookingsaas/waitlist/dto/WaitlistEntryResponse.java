package dev.capibyte.bookingsaas.waitlist.dto;

import dev.capibyte.bookingsaas.waitlist.WaitlistEntry;
import dev.capibyte.bookingsaas.waitlist.WaitlistStatus;
import java.time.LocalDate;
import java.util.UUID;

public record WaitlistEntryResponse(UUID id, UUID professionalId, UUID serviceId, LocalDate date, String clientName,
		String clientEmail, String clientPhone, WaitlistStatus status) {

	public static WaitlistEntryResponse from(WaitlistEntry entry) {
		return new WaitlistEntryResponse(entry.getId(), entry.getProfessionalId(), entry.getServiceId(),
				entry.getDate(), entry.getClientName(), entry.getClientEmail(), entry.getClientPhone(),
				entry.getStatus());
	}
}
