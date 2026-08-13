package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.staff.Professional;
import java.util.List;
import java.util.UUID;

public record PublicProfessionalResponse(UUID id, String displayName, String bio, String photoUrl,
		List<PublicWeeklyAvailabilityResponse> hours) {

	public static PublicProfessionalResponse from(Professional professional,
			List<PublicWeeklyAvailabilityResponse> hours) {
		return new PublicProfessionalResponse(professional.getId(), professional.getDisplayName(),
				professional.getBio(), professional.getPhotoUrl(), hours);
	}
}
