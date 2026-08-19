package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentGroupRequest;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentRequest;
import dev.capibyte.bookingsaas.booking.dto.PublicBranchHoursResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicBranchResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicProfessionalResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicServiceComboResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicServiceResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicTenantResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicWeeklyAvailabilityResponse;
import dev.capibyte.bookingsaas.catalog.ServiceCombo;
import dev.capibyte.bookingsaas.catalog.ServiceComboService;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.staff.WeeklyAvailabilityService;
import dev.capibyte.bookingsaas.tenant.Branch;
import dev.capibyte.bookingsaas.tenant.BranchHoursService;
import dev.capibyte.bookingsaas.tenant.BranchService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No auth here — {@link PublicTenantResolutionFilter} resolves the tenant from {tenantSlug}
 * before this runs. Clients aren't required to have accounts for the MVP; a booking just
 * captures name/email/phone directly.
 */
@RestController
@RequestMapping("/api/public/{tenantSlug}")
@RequiredArgsConstructor
public class PublicBookingController {

	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;
	private final WeeklyAvailabilityService weeklyAvailabilityService;
	private final BranchService branchService;
	private final BranchHoursService branchHoursService;
	private final PublicAvailabilityService publicAvailabilityService;
	private final AppointmentService appointmentService;
	private final TenantService tenantService;
	private final ServiceComboService serviceComboService;

	@GetMapping
	public PublicTenantResponse tenant(@PathVariable String tenantSlug) {
		return PublicTenantResponse.from(tenantService.findById(TenantContext.getTenantId()));
	}

	@GetMapping("/branches")
	public List<PublicBranchResponse> branches(@PathVariable String tenantSlug) {
		return branchService.findAll().stream()
				.filter(Branch::isActive)
				.map(branch -> PublicBranchResponse.from(branch, branchHoursService.findByBranch(branch.getId())
						.stream().map(PublicBranchHoursResponse::from).toList()))
				.toList();
	}

	/**
	 * {@code branchId} is optional: a single-branch tenant (most of them, today) never needs a
	 * client to pick one, so the frontend only sends it once GET .../branches returns more than
	 * one. Filtering happens by "is this service offered by an active professional at that
	 * branch", not a direct service-branch link — the catalog itself isn't branch-scoped (see
	 * ServiceOfferingService.isOfferedAtBranch).
	 */
	@GetMapping("/services")
	public List<PublicServiceResponse> services(@PathVariable String tenantSlug,
			@RequestParam(required = false) UUID branchId) {
		return serviceOfferingService.findAll().stream()
				.filter(ServiceOffering::isActive)
				.filter(s -> branchId == null || serviceOfferingService.isOfferedAtBranch(s.getId(), branchId))
				.map(PublicServiceResponse::from)
				.toList();
	}

	/**
	 * {@code serviceId} is optional: when given, only professionals assigned to that service come
	 * back (the booking flow, step by step); when omitted, every active professional for the
	 * tenant (optionally narrowed by {@code branchId}) comes back — e.g. for a "meet the team"
	 * carousel shown before a client picks a service.
	 */
	@GetMapping("/professionals")
	public List<PublicProfessionalResponse> professionals(@PathVariable String tenantSlug,
			@RequestParam(required = false) UUID serviceId, @RequestParam(required = false) UUID branchId) {
		List<Professional> professionals = serviceId == null
				? professionalService.findAll()
				: serviceOfferingService.findProfessionalIdsForService(serviceId).stream()
						.map(professionalService::findById)
						.toList();
		return professionals.stream()
				.filter(Professional::isActive)
				.filter(p -> branchId == null || p.getBranchId().equals(branchId))
				.map(p -> PublicProfessionalResponse.from(p, weeklyAvailabilityService.findByProfessional(p.getId())
						.stream().map(PublicWeeklyAvailabilityResponse::from).toList()))
				.toList();
	}

	@GetMapping("/availability")
	public List<TimeSlot> availability(@PathVariable String tenantSlug, @RequestParam UUID professionalId,
			@RequestParam UUID serviceId, @RequestParam LocalDate date,
			@RequestParam(required = false) LocalTime preferredAfter) {
		return publicAvailabilityService.findFreeSlots(professionalId, serviceId, date, preferredAfter);
	}

	/**
	 * Preview-only ("would these two services get a combo price if booked together?") — the client
	 * queries this while building a multi-service booking to decide what to show, but the actual
	 * charge is always re-derived server-side by {@link AppointmentService#bookGroup}, never trusted
	 * from here. 204 (not 404) when no combo applies — this is a normal, expected outcome for most
	 * service pairs, not an error.
	 */
	@GetMapping("/service-combo")
	public ResponseEntity<PublicServiceComboResponse> serviceCombo(@PathVariable String tenantSlug,
			@RequestParam UUID serviceAId, @RequestParam UUID serviceBId) {
		Optional<ServiceCombo> combo = serviceComboService.findApplicableCombo(serviceAId, serviceBId);
		return combo.map(c -> ResponseEntity.ok(PublicServiceComboResponse.from(c)))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/appointments")
	@ResponseStatus(HttpStatus.CREATED)
	public AppointmentResponse book(@PathVariable String tenantSlug, @Valid @RequestBody BookAppointmentRequest request) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		Instant startTime = ZonedDateTime.of(request.date(), request.startTime(), zone).toInstant();
		// GET .../availability already excludes past slots for today (see
		// PublicAvailabilityService), but that's just what the client is shown — nothing stopped a
		// direct POST here with an already-past time. Only guarded on the public path: the owner's
		// own manual booking (AppointmentController) deliberately allows backdating, e.g. to log a
		// walk-in that already happened.
		if (startTime.isBefore(Instant.now())) {
			throw new BadRequestException("Can't book an appointment in the past");
		}
		Appointment appointment = appointmentService.book(request.professionalId(), request.serviceId(), startTime,
				request.clientName(), request.clientEmail(), request.clientPhone(), request.clientInstagram());
		return AppointmentResponse.from(appointment, appointmentService.findClient(appointment.getClientId()));
	}

	/**
	 * Two or more services booked together — see {@link AppointmentService#bookGroup}. Same
	 * "no past" guard as {@link #book}, applied per item, before any of them reach the service layer.
	 */
	@PostMapping("/appointments/group")
	@ResponseStatus(HttpStatus.CREATED)
	public List<AppointmentResponse> bookGroup(@PathVariable String tenantSlug,
			@Valid @RequestBody BookAppointmentGroupRequest request) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		Instant now = Instant.now();
		List<BookGroupItem> items = request.items().stream().map(item -> {
			Instant startTime = ZonedDateTime.of(item.date(), item.startTime(), zone).toInstant();
			if (startTime.isBefore(now)) {
				throw new BadRequestException("Can't book an appointment in the past");
			}
			return new BookGroupItem(item.professionalId(), item.serviceId(), startTime);
		}).toList();
		List<Appointment> appointments = appointmentService.bookGroup(items, request.clientName(),
				request.clientEmail(), request.clientPhone(), request.clientInstagram());
		return appointments.stream()
				.map(a -> AppointmentResponse.from(a, appointmentService.findClient(a.getClientId())))
				.toList();
	}
}
