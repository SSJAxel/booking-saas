package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.tenant.Branch;
import java.util.List;
import java.util.UUID;

public record PublicBranchResponse(UUID id, String name, String address, String phone, String googleBusinessUrl,
		List<PublicBranchHoursResponse> hours) {

	public static PublicBranchResponse from(Branch branch, List<PublicBranchHoursResponse> hours) {
		return new PublicBranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.getPhone(),
				branch.getGoogleBusinessUrl(), hours);
	}
}
