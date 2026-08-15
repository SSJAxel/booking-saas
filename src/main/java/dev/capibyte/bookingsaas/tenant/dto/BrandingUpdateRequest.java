package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional — clearing branding back to null (unbranded) is a valid request.
 * {@code logoUrl}/{@code bannerUrl} aren't restricted to an http(s) scheme like the others below —
 * they come from FileStorageService's own upload flow as server-relative paths
 * (frontend's resolveMediaUrl prefixes them with the API base), not user-typed URLs. */
public record BrandingUpdateRequest(
		@Size(max = 500) String logoUrl,
		@Size(max = 500) String bannerUrl,
		@Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #RRGGBB") String accentColor,
		@Size(max = 255) String tagline,
		@Email @Size(max = 255) String contactEmail,
		@Pattern(regexp = "^[+0-9 ()-]{6,30}$",
				message = "must be a phone number (digits, spaces, +, -, ( ) only)") String whatsappNumber,
		@Size(max = 255) String transferAlias,
		// instagramUrl/facebookUrl are rendered as a bare <a href> on the public booking page
		// (SideMenu.jsx) with no client-side sanitization — an http(s)-only scheme stops a
		// tenant-controlled "javascript:" URL from executing in a visitor's browser on click.
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String instagramUrl,
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String facebookUrl,
		// Loaded as a live <script src> (InstagramFeed.jsx) for every visitor, no click needed —
		// same http(s)-only restriction, since a real embed-widget URL is always https anyway.
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String instagramFeedUrl) {
}
