package dev.capibyte.bookingsaas.identity.dto;

import java.util.UUID;

/**
 * Deliberately carries no token — the account exists but can't log in yet until the owner clicks
 * the verification link mailed to them. See {@link VerifyEmailRequest} for what completes it.
 */
public record RegisterResponse(UUID tenantId, String tenantSlug, String email) {
}
