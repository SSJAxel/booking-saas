package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.DateAvailabilityService;
import dev.capibyte.bookingsaas.staff.TimeOff;
import dev.capibyte.bookingsaas.staff.TimeOffService;
import dev.capibyte.bookingsaas.staff.WeeklyAvailabilityService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicAvailabilityService {

	private final ServiceOfferingService serviceOfferingService;
	private final WeeklyAvailabilityService weeklyAvailabilityService;
	private final TimeOffService timeOffService;
	private final DateAvailabilityService dateAvailabilityService;
	private final AppointmentRepository appointmentRepository;
	private final TenantService tenantService;
	private final AvailabilityCalculator calculator;

	@Transactional(readOnly = true)
	public List<TimeSlot> findFreeSlots(UUID professionalId, UUID serviceId, LocalDate date) {
		return findFreeSlots(professionalId, serviceId, date, null);
	}

	/**
	 * {@code preferredAfter}, when given, reorders (never filters) the result so slots at or after
	 * that time come first — used while picking the 2nd+ leg of a multi-service booking
	 * ({@code BookGroupItem}), to surface back-to-back-friendly options (e.g. right after the first
	 * service ends) without hiding earlier-in-the-day or other-day options the client might still
	 * prefer.
	 */
	@Transactional(readOnly = true)
	public List<TimeSlot> findFreeSlots(UUID professionalId, UUID serviceId, LocalDate date, LocalTime preferredAfter) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		List<TimeSlot> slots = computeSlots(professionalId, serviceId, date, zone);
		// The calculator only knows about weekly hours/time-off/other bookings — it has no notion
		// of "now", so a client browsing today's date would otherwise see (and could book) a slot
		// earlier today that's already gone. Only matters for today; a future date has nothing to
		// filter since every slot on it is already in the future.
		if (date.equals(LocalDate.now(zone))) {
			LocalTime now = LocalTime.now(zone);
			slots = slots.stream().filter(slot -> !slot.start().isBefore(now)).toList();
		}
		if (preferredAfter != null) {
			List<TimeSlot> onOrAfter = slots.stream().filter(slot -> !slot.start().isBefore(preferredAfter)).toList();
			List<TimeSlot> before = slots.stream().filter(slot -> slot.start().isBefore(preferredAfter)).toList();
			slots = Stream.concat(onOrAfter.stream(), before.stream()).toList();
		}
		return slots;
	}

	/**
	 * Same computation as {@link #findFreeSlots}, minus the "exclude slots already past for today"
	 * filter — used by {@code AppointmentService.book()} to validate an owner's manual booking,
	 * which must still respect working hours/time-off/other bookings but is deliberately allowed to
	 * be backdated (e.g. logging a walk-in that already happened this morning; see
	 * AppointmentController's Javadoc on why the public path enforces "no past" and this one
	 * doesn't).
	 */
	@Transactional(readOnly = true)
	public List<TimeSlot> computeSlotsIgnoringPast(UUID professionalId, UUID serviceId, LocalDate date) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		return computeSlots(professionalId, serviceId, date, zone);
	}

	private List<TimeSlot> computeSlots(UUID professionalId, UUID serviceId, LocalDate date, ZoneId zone) {
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

		// Two independent, additive sources of "open" for this date: the recurring weekly pattern
		// for its day of week, and any specific-date entries (DateAvailability) — for a professional
		// who releases capacity one calendar date at a time rather than committing to a recurring
		// rule, e.g. someone who works by season. Neither is exclusive of the other.
		List<TimeSlot> openWindows = new ArrayList<>();
		weeklyAvailabilityService.findByProfessionalAndDay(professionalId, date.getDayOfWeek())
				.stream()
				.map(w -> new TimeSlot(w.getStartTime(), w.getEndTime()))
				.forEach(openWindows::add);
		dateAvailabilityService.findByProfessionalAndDate(professionalId, date).stream()
				.map(d -> new TimeSlot(d.getStartTime(), d.getEndTime()))
				.forEach(openWindows::add);

		List<TimeSlot> blocked = new ArrayList<>();
		dayTimeOffs.stream()
				.filter(t -> t.getStartTime() != null && t.getEndTime() != null)
				.map(t -> new TimeSlot(t.getStartTime(), t.getEndTime()))
				.forEach(blocked::add);

		// Appointment times are stored as Instants (absolute); converted back to this tenant's own
		// wall-clock time so they compare correctly against the LocalTime weekly-hours/time-off
		// windows above, which were always tenant-local wall-clock to begin with.
		Instant dayStart = date.atStartOfDay(zone).toInstant();
		Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
		appointmentRepository
				.findAllByProfessionalIdAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusNotIn(professionalId,
						dayStart, dayEnd, AppointmentService.INACTIVE_STATUSES)
				.stream()
				.map(a -> new TimeSlot(LocalTime.ofInstant(a.getStartTime(), zone),
						LocalTime.ofInstant(a.getEndTime(), zone)))
				.forEach(blocked::add);

		return calculator.freeSlots(openWindows, blocked, service.getDurationMinutes());
	}
}
