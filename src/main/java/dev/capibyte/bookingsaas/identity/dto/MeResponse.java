package dev.capibyte.bookingsaas.identity.dto;

import java.util.UUID;

public record MeResponse(UUID userId, UUID tenantId, String role) {
}
