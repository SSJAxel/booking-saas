package dev.capibyte.bookingsaas.tenant;

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

/** A recurring weekly open window for a branch, e.g. "Monday 09:00-18:00". Informational for now —
 * not yet consulted by {@link dev.capibyte.bookingsaas.booking.PublicAvailabilityService}, which
 * still computes free slots from professionals' own {@link dev.capibyte.bookingsaas.staff.WeeklyAvailability} only. */
@Entity
@Table(name = "branch_hours")
@Getter
@Setter
@NoArgsConstructor
public class BranchHours extends BaseTenantEntity {

	@Column(name = "branch_id", nullable = false)
	private UUID branchId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;
}
