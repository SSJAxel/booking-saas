package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentRequest;
import dev.capibyte.bookingsaas.booking.dto.StatusTransitionRequest;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.TenantService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class AppointmentController {

	private final AppointmentService appointmentService;
	private final TenantService tenantService;

	@GetMapping
	public List<AppointmentResponse> list(
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID professionalId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) AppointmentStatus status) {
		return appointmentService.search(branchId, professionalId, from, to, status).stream()
				.map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public AppointmentResponse get(@PathVariable UUID id) {
		return toResponse(appointmentService.findById(id));
	}

	/**
	 * Owner-side manual booking (walk-ins, phone bookings) — same request shape and same
	 * double-booking guarantee as the public booking flow, just authenticated instead of going
	 * through {tenantSlug}.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AppointmentResponse create(@Valid @RequestBody BookAppointmentRequest request) {
		Instant startTime = toInstant(request);
		return toResponse(appointmentService.book(request.professionalId(), request.serviceId(), startTime,
				request.clientName(), request.clientEmail(), request.clientPhone(), request.clientInstagram()));
	}

	/**
	 * "Sobreturno" / walk-in override: the client of {@code id} called to cancel outside the
	 * system, so free that slot and hand it to a new client in one action. See {@link
	 * AppointmentService#replace} for why this is a cancel-then-book, not a true overlapping
	 * booking — the no_double_booking constraint is deliberately never bypassed, including here.
	 */
	@PostMapping("/{id}/replace")
	public AppointmentResponse replace(@PathVariable UUID id, @Valid @RequestBody BookAppointmentRequest request) {
		Instant startTime = toInstant(request);
		return toResponse(appointmentService.replace(id, request.professionalId(), request.serviceId(), startTime,
				request.clientName(), request.clientEmail(), request.clientPhone(), request.clientInstagram()));
	}

	private Instant toInstant(BookAppointmentRequest request) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		return ZonedDateTime.of(request.date(), request.startTime(), zone).toInstant();
	}

	@PatchMapping("/{id}/status")
	public AppointmentResponse transition(@PathVariable UUID id, @Valid @RequestBody StatusTransitionRequest request) {
		return toResponse(appointmentService.transitionStatus(id, request.status()));
	}

	/** Manual counterpart to the MercadoPago webhook — lets the owner confirm a deposit paid by
	 * bank transfer (see Tenant.transferAlias) instead of an automated payment provider. */
	@PatchMapping("/{id}/confirm-deposit")
	public AppointmentResponse confirmDeposit(@PathVariable UUID id) {
		return toResponse(appointmentService.markDepositPaid(id));
	}

	private AppointmentResponse toResponse(Appointment appointment) {
		return AppointmentResponse.from(appointment, appointmentService.findClient(appointment.getClientId()));
	}
}
