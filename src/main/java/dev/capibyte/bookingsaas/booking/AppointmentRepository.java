package dev.capibyte.bookingsaas.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

	List<Appointment> findAllByProfessionalIdAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusNotIn(
			UUID professionalId, Instant from, Instant to, List<AppointmentStatus> excludedStatuses);

	List<Appointment> findAllByStatusAndPaymentStatusAndCreatedAtBefore(AppointmentStatus status,
			PaymentStatus paymentStatus, Instant cutoff);

	long deleteByStartTimeBefore(Instant cutoff);

	long deleteByStartTimeBetween(Instant from, Instant to);
}
