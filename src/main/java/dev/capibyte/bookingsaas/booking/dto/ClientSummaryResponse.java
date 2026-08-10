package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.Client;
import java.util.UUID;

/** Search-result shape for the "fijar cliente" picker in the Mejores clientes panel. */
public record ClientSummaryResponse(UUID id, String name, String email, String phone, int rating, boolean pinned) {

	public static ClientSummaryResponse from(Client client) {
		return new ClientSummaryResponse(client.getId(), client.getName(), client.getEmail(), client.getPhone(),
				client.getRating(), client.isPinned());
	}
}
