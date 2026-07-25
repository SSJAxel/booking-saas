package dev.capibyte.bookingsaas.tenant.dto;

import dev.capibyte.bookingsaas.tenant.Branch;
import java.util.UUID;

public record BranchResponse(UUID id, String name, String address, String phone, boolean active) {

	public static BranchResponse from(Branch branch) {
		return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.getPhone(),
				branch.isActive());
	}
}
