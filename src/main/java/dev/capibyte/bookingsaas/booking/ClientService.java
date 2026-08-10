package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

	private final ClientRepository clientRepository;

	@Transactional(readOnly = true)
	public List<Client> search(String query) {
		return clientRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
	}

	/** "Cliente fijo": kept in the Mejores clientes panel regardless of {@code rating} — see
	 * Client's Javadoc for why a tenant would want this. */
	@Transactional
	public Client setPinned(UUID id, boolean pinned) {
		Client client = clientRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Client not found: " + id));
		client.setPinned(pinned);
		return client;
	}
}
