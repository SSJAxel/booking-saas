package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Separate from {@link BranchRequest} (create) the same way ProfessionalUpdateRequest is split
 * from ProfessionalRequest — {@code active} is boxed and required so a client that forgets to send
 * it fails validation instead of silently deactivating the branch (a primitive default of false
 * would do exactly that). */
public record BranchUpdateRequest(@NotBlank String name, String address, String phone, String googleBusinessUrl,
		@NotNull Boolean active) {
}
