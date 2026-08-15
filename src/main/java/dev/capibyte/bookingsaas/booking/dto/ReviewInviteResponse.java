package dev.capibyte.bookingsaas.booking.dto;

/** Context for the public review form's header ("Turno con Ana — Corte") — GET
 * /api/public/{tenantSlug}/reviews/invite/{token}. No appointment internals leaked, just enough to
 * greet the client and remind them which visit this is about. */
public record ReviewInviteResponse(String clientName, String professionalName, String serviceName) {
}
