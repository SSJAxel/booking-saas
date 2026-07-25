package dev.capibyte.bookingsaas.waitlist;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

	Optional<WaitlistEntry> findFirstByProfessionalIdAndServiceIdAndDateAndStatusOrderByCreatedAtAsc(
			UUID professionalId, UUID serviceId, LocalDate date, WaitlistStatus status);

	List<WaitlistEntry> findAllByProfessionalIdAndDateAndStatus(UUID professionalId, LocalDate date,
			WaitlistStatus status);
}
