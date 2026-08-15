package dev.capibyte.bookingsaas.staff.dto;

import dev.capibyte.bookingsaas.staff.Professional;
import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalResponse(UUID id, UUID branchId, String displayName, String bio, String photoUrl,
		boolean active, BigDecimal serviceCommissionRate, BigDecimal productCommissionRate) {

	public static ProfessionalResponse from(Professional professional) {
		return new ProfessionalResponse(professional.getId(), professional.getBranchId(),
				professional.getDisplayName(), professional.getBio(), professional.getPhotoUrl(),
				professional.isActive(), professional.getServiceCommissionRate(),
				professional.getProductCommissionRate());
	}
}
