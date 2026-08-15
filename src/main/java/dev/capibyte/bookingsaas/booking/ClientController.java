package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.ClientSummaryResponse;
import dev.capibyte.bookingsaas.booking.dto.ClientVisitResponse;
import dev.capibyte.bookingsaas.booking.dto.PinClientRequest;
import dev.capibyte.bookingsaas.booking.dto.UpdateClientNotesRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class ClientController {

	private final ClientService clientService;

	/** Backs the "fijar cliente" search box in the Mejores clientes panel — name or email,
	 * partial match. Empty/blank query returns nothing rather than the whole client list. */
	@GetMapping("/search")
	public List<ClientSummaryResponse> search(@RequestParam String q) {
		if (q == null || q.isBlank()) {
			return List.of();
		}
		return clientService.search(q).stream().map(ClientSummaryResponse::from).limit(20).toList();
	}

	/** Owner/admin: pin or unpin a "cliente fijo" — see Client's Javadoc. */
	@PatchMapping("/{id}/pin")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public ClientSummaryResponse setPinned(@PathVariable UUID id, @Valid @RequestBody PinClientRequest request) {
		return ClientSummaryResponse.from(clientService.setPinned(id, request.pinned()));
	}

	/** Owner/admin: freeform notes about this client — "keep track of this client" tool, not a
	 * receptionist action, so it doesn't share STAFF's default read access below. */
	@PatchMapping("/{id}/notes")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public ClientSummaryResponse updateNotes(@PathVariable UUID id, @Valid @RequestBody UpdateClientNotesRequest request) {
		return ClientSummaryResponse.from(clientService.updateNotes(id, request.notes()));
	}

	/** Every visit this client has ever had, most recent first — who served them, when, what
	 * service, what status. Backs ClientHistoryModal.jsx. */
	@GetMapping("/{id}/history")
	public List<ClientVisitResponse> history(@PathVariable UUID id) {
		return clientService.history(id);
	}
}
