package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.TimeOff;
import dev.capibyte.bookingsaas.staff.TimeOffService;
import dev.capibyte.bookingsaas.staff.WeeklyAvailabilityService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicAvailabilityService {

	private final ServiceOfferingService serviceOfferingService;
	private final WeeklyAvailabilityService weeklyAvailabilityService;
	private final TimeOffService timeOffService;
	private final AppointmentService appointmentService;
	private final TenantService tenantService;
	private final AvailabilityCalculator calculator;

	@Transactional(readOnly = true)
	public List<TimeSlot> findFreeSlots(UUID professionalId, UUID serviceId, LocalDate date) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		ServiceOffering service = serviceOfferingService.findById(serviceId);

		List<UUID> eligibleProfessionals = serviceOfferingService.findProfessionalIdsForService(serviceId);
		if (!eligibleProfessionals.contains(professionalId)) {
			throw new NotFoundException("This professional does not offer the requested service");
		}

		List<TimeOff> dayTimeOffs = timeOffService.findByProfessionalAndDate(professionalId, date);
		boolean fullDayOff = dayTimeOffs.stream().anyMatch(t -> t.getStartTime() == null && t.getEndTime() == null);
		if (fullDayOff) {
			return List.of();
		}

		List<TimeSlot> openWindows = weeklyAvailabilityService.findByProfessionalAndDay(professionalId, date.getDayOfWeek())
				.stream()
				.map(w -> new TimeSlot(w.getStartTime(), w.getEndTime()))
				.toList();

		List<TimeSlot> blocked = new ArrayList<>();
		dayTimeOffs.stream()
				.filter(t -> t.getStartTime() != null && t.getEndTime() != null)
				.map(t -> new TimeSlot(t.getStartTime(), t.getEndTime()))
				.forEach(blocked::add);

		// Appointment times are stored as Instants (absolute); converted back to this tenant's own
		// wall-clock time so they compare correctly against the LocalTime weekly-hours/time-off
		// windows above, which were always tenant-local wall-clock to begin with.
		appointmentService.findActiveByProfessionalAndDay(professionalId, date, zone).stream()
				.map(a -> new TimeSlot(LocalTime.ofInstant(a.getStartTime(), zone),
						LocalTime.ofInstant(a.getEndTime(), zone)))
				.forEach(blocked::add);

		return calculator.freeSlots(openWindows, blocked, service.getDurationMinutes());
	}
}
