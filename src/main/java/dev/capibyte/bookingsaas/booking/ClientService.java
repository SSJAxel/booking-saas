package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.ClientVisitResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

	private final ClientRepository clientRepository;
	private final AppointmentRepository appointmentRepository;
	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;

	@Transactional(readOnly = true)
	public List<Client> search(String query) {
		return clientRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
	}

	/** "Cliente fijo": kept in the Mejores clientes panel regardless of {@code rating} — see
	 * Client's Javadoc for why a tenant would want this. */
	@Transactional
	public Client setPinned(UUID id, boolean pinned) {
		Client client = findById(id);
		client.setPinned(pinned);
		return client;
	}

	@Transactional
	public Client updateNotes(UUID id, String notes) {
		Client client = findById(id);
		client.setNotes(notes);
		return client;
	}

	/** Every visit this client has ever had with the tenant, most recent first — see the "who did
	 * they see, when" ask this backs (ClientHistoryModal.jsx on the frontend). */
	@Transactional(readOnly = true)
	public List<ClientVisitResponse> history(UUID id) {
		findById(id); // 404s if missing or belongs to another tenant, before touching appointments
		return appointmentRepository.findByClientIdOrderByStartTimeDesc(id).stream()
				.map(a -> new ClientVisitResponse(a.getId(), a.getStartTime(),
						serviceOfferingService.findById(a.getServiceId()).getName(),
						professionalService.findById(a.getProfessionalId()).getDisplayName(), a.getStatus()))
				.toList();
	}

	private Client findById(UUID id) {
		return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found: " + id));
	}
}
