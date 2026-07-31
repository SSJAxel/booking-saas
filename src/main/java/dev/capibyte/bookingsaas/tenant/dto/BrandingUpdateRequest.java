package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional — clearing branding back to null (unbranded) is a valid request. */
public record BrandingUpdateRequest(
		@Size(max = 500) String logoUrl,
		@Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #RRGGBB") String accentColor,
		@Size(max = 255) String tagline) {
}
