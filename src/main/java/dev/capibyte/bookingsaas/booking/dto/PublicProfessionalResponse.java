package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.staff.Professional;
import java.util.UUID;

public record PublicProfessionalResponse(UUID id, String displayName, String bio, String photoUrl) {

	public static PublicProfessionalResponse from(Professional professional) {
		return new PublicProfessionalResponse(professional.getId(), professional.getDisplayName(),
				professional.getBio(), professional.getPhotoUrl());
	}
}
