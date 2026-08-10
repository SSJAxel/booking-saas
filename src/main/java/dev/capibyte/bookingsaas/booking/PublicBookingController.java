package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentRequest;
import dev.capibyte.bookingsaas.booking.dto.PublicBranchResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicProfessionalResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicServiceResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicTenantResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.Branch;
import dev.capibyte.bookingsaas.tenant.BranchService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
	private final BranchService branchService;
	private final PublicAvailabilityService publicAvailabilityService;
	private final AppointmentService appointmentService;
	private final TenantService tenantService;

	@GetMapping
	public PublicTenantResponse tenant(@PathVariable String tenantSlug) {
		return PublicTenantResponse.from(tenantService.findById(TenantContext.getTenantId()));
	}

	@GetMapping("/branches")
	public List<PublicBranchResponse> branches(@PathVariable String tenantSlug) {
		return branchService.findAll().stream()
				.filter(Branch::isActive)
				.map(PublicBranchResponse::from)
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

	@GetMapping("/professionals")
	public List<PublicProfessionalResponse> professionals(@PathVariable String tenantSlug, @RequestParam UUID serviceId,
			@RequestParam(required = false) UUID branchId) {
		return serviceOfferingService.findProfessionalIdsForService(serviceId).stream()
				.map(professionalService::findById)
				.filter(Professional::isActive)
				.filter(p -> branchId == null || p.getBranchId().equals(branchId))
				.map(PublicProfessionalResponse::from)
				.toList();
	}

	@GetMapping("/availability")
	public List<TimeSlot> availability(@PathVariable String tenantSlug, @RequestParam UUID professionalId,
			@RequestParam UUID serviceId, @RequestParam LocalDate date) {
		return publicAvailabilityService.findFreeSlots(professionalId, serviceId, date);
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
}
