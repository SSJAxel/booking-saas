package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional — clearing branding back to null (unbranded) is a valid request. */
public record BrandingUpdateRequest(
		@Size(max = 500) String logoUrl,
		@Size(max = 500) String bannerUrl,
		@Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #RRGGBB") String accentColor,
		@Size(max = 255) String tagline,
		@Email @Size(max = 255) String contactEmail,
		@Pattern(regexp = "^[+0-9 ()-]{6,30}$",
				message = "must be a phone number (digits, spaces, +, -, ( ) only)") String whatsappNumber,
		@Size(max = 255) String transferAlias) {
}
