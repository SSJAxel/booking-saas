package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.booking.Client;
import java.time.LocalDate;
import java.util.UUID;

/** Search-result shape for the "fijar cliente" picker in the Mejores clientes panel, also the
 * response shape for pin/notes/birthday/redeem-reward mutations and the "cumpleaños del mes" list. */
public record ClientSummaryResponse(UUID id, String name, String email, String phone, int rating, boolean pinned,
		String notes, int loyaltyPoints, LocalDate birthDate) {

	public static ClientSummaryResponse from(Client client) {
		return new ClientSummaryResponse(client.getId(), client.getName(), client.getEmail(), client.getPhone(),
				client.getRating(), client.isPinned(), client.getNotes(), client.getLoyaltyPoints(),
				client.getBirthDate());
	}
}
