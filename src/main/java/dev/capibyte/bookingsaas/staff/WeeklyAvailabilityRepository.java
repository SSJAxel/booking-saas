package dev.capibyte.bookingsaas.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyAvailabilityRepository extends JpaRepository<WeeklyAvailability, UUID> {

	List<WeeklyAvailability> findAllByProfessionalId(UUID professionalId);

	List<WeeklyAvailability> findAllByProfessionalIdAndDayOfWeek(UUID professionalId, java.time.DayOfWeek dayOfWeek);
}
