package dev.capibyte.bookingsaas.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/** "Cumpleaños del mes" panel list — only the month matters, ordered by day so the list reads
	 * top-to-bottom in calendar order regardless of what today's date is. EXTRACT(... FROM ...), not
	 * the legacy MONTH()/DAY() HQL functions — Hibernate 6's rewritten query engine doesn't register
	 * those the same way Hibernate 5 did. */
	@Query("SELECT c FROM Client c WHERE c.birthDate IS NOT NULL AND EXTRACT(MONTH FROM c.birthDate) = :month ORDER BY EXTRACT(DAY FROM c.birthDate)")
	List<Client> findByBirthMonth(@Param("month") int month);

	/** BirthdayEmailScheduler's actual "who's today" lookup — deliberately month+day, never the
	 * year (nothing here computes an age). */
	@Query("SELECT c FROM Client c WHERE c.birthDate IS NOT NULL AND EXTRACT(MONTH FROM c.birthDate) = :month AND EXTRACT(DAY FROM c.birthDate) = :day")
	List<Client> findByBirthMonthAndDay(@Param("month") int month, @Param("day") int day);
}
