package dev.capibyte.bookingsaas.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DateAvailabilityRepository extends JpaRepository<DateAvailability, UUID> {

	List<DateAvailability> findAllByProfessionalId(UUID professionalId);

	List<DateAvailability> findAllByProfessionalIdAndDate(UUID professionalId, LocalDate date);
}
