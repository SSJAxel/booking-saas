package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentRequest;
import dev.capibyte.bookingsaas.booking.dto.PublicProfessionalResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicServiceResponse;
import dev.capibyte.bookingsaas.booking.dto.PublicTenantResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
	private final PublicAvailabilityService publicAvailabilityService;
	private final AppointmentService appointmentService;
	private final TenantService tenantService;

	@GetMapping
	public PublicTenantResponse tenant(@PathVariable String tenantSlug) {
		return PublicTenantResponse.from(tenantService.findById(TenantContext.getTenantId()));
	}

	@GetMapping("/services")
	public List<PublicServiceResponse> services(@PathVariable String tenantSlug) {
		return serviceOfferingService.findAll().stream()
				.filter(ServiceOffering::isActive)
				.map(PublicServiceResponse::from)
				.toList();
	}

	@GetMapping("/professionals")
	public List<PublicProfessionalResponse> professionals(@PathVariable String tenantSlug, @RequestParam UUID serviceId) {
		return serviceOfferingService.findProfessionalIdsForService(serviceId).stream()
				.map(professionalService::findById)
				.filter(Professional::isActive)
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
		Appointment appointment = appointmentService.book(request.professionalId(), request.serviceId(),
				request.startTime(), request.clientName(), request.clientEmail(), request.clientPhone());
		return AppointmentResponse.from(appointment, appointmentService.findClient(appointment.getClientId()));
	}
}
