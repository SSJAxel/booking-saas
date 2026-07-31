package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Branch;
import java.util.UUID;

public record PublicBranchResponse(UUID id, String name, String address) {

	public static PublicBranchResponse from(Branch branch) {
		return new PublicBranchResponse(branch.getId(), branch.getName(), branch.getAddress());
	}
}
