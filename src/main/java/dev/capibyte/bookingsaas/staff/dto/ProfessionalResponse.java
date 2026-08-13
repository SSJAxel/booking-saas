package dev.capibyte.bookingsaas.staff.dto;

import dev.capibyte.bookingsaas.staff.Professional;
import java.util.UUID;

public record ProfessionalResponse(UUID id, UUID branchId, String displayName, String bio, String photoUrl,
		boolean active) {

	public static ProfessionalResponse from(Professional professional) {
		return new ProfessionalResponse(professional.getId(), professional.getBranchId(),
				professional.getDisplayName(), professional.getBio(), professional.getPhotoUrl(),
				professional.isActive());
	}
}
