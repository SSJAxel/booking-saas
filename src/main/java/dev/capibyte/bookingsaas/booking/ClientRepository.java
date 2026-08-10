package dev.capibyte.bookingsaas.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

	Optional<Client> findByEmail(String email);

	/** Second identity check when the email doesn't match — same person rebooking with a
	 * different email but the same phone shouldn't get treated as a brand-new client (and lose
	 * their rating history). List, not Optional: phone has no unique constraint (unlike email), so
	 * a coincidental duplicate must not blow up the query — see
	 * AppointmentService.findOrCreateClient, which just takes the first match. */
	List<Client> findByPhone(String phone);

	/** Backs the "pin a client" search box — name OR email, partial, case-insensitive. */
	List<Client> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
}
