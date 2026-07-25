package dev.capibyte.bookingsaas.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

	List<TimeOff> findAllByProfessionalId(UUID professionalId);

	List<TimeOff> findAllByProfessionalIdAndDate(UUID professionalId, LocalDate date);
}
