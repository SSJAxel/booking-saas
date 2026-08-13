package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The mirror image of {@link TimeOff}: opens a specific date regardless of its day of week,
 * instead of closing one that would otherwise be open per {@link WeeklyAvailability}. Additive
 * with the weekly schedule, not exclusive — a professional can rely on both, or on this alone with
 * an empty weekly schedule (e.g. someone who works by season and never has a fixed weekly pattern).
 */
@Entity
@Table(name = "date_availabilities")
@Getter
@Setter
@NoArgsConstructor
public class DateAvailability extends BaseTenantEntity {

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(nullable = false)
	private LocalDate date;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;
}
