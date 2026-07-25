package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A recurring weekly slot, e.g. "Monday 09:00-17:00". {@link dev.capibyte.bookingsaas.staff.TimeOff} overrides it for specific dates. */
@Entity
@Table(name = "weekly_availabilities")
@Getter
@Setter
@NoArgsConstructor
public class WeeklyAvailability extends BaseTenantEntity {

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;
}
