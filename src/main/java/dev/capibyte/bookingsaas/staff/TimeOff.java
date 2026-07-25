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

/** Null start/end time means the whole day is off. */
@Entity
@Table(name = "time_offs")
@Getter
@Setter
@NoArgsConstructor
public class TimeOff extends BaseTenantEntity {

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(nullable = false)
	private LocalDate date;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column
	private String reason;
}
