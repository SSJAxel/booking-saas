package dev.capibyte.bookingsaas.identity.dto;

import java.util.UUID;

public record AuthResponse(
		String token,
		UUID tenantId,
		String tenantSlug,
		UUID userId,
		String email,
		String role) {
}
