package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.BookAppointmentRequest;
import dev.capibyte.bookingsaas.booking.dto.HistoryPurgeResponse;
import dev.capibyte.bookingsaas.booking.dto.RescheduleRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
	 * "Reagendar": moves an existing appointment to a new date/time for the same client, professional
	 * and service — see {@link AppointmentService#reschedule} for what {@code reason} controls
	 * (rating impact and whether an already-paid deposit is kept).
	 */
	@PostMapping("/{id}/reschedule")
	public AppointmentResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
		Instant newStartTime = toInstant(request);
		return toResponse(appointmentService.reschedule(id, newStartTime, request.reason()));
	}

	/**
	 * "Sobreturno": a manual booking allowed to overlap another appointment of the same
	 * professional on purpose (e.g. a long service in progress while the professional also takes a
	 * quick one) — the owner is the only one who can decide this, never automatic and never
	 * reachable from the public booking flow. {@code overtime=true} is hardcoded here, not read
	 * from the request body: {@link BookAppointmentRequest} has no such field at all, so there is
	 * no way to trigger this from anywhere except this exact endpoint.
	 */
	@PostMapping("/overtime")
	@ResponseStatus(HttpStatus.CREATED)
	public AppointmentResponse createOvertime(@Valid @RequestBody BookAppointmentRequest request) {
		Instant startTime = toInstant(request);
		return toResponse(appointmentService.book(request.professionalId(), request.serviceId(), startTime,
				request.clientName(), request.clientEmail(), request.clientPhone(), request.clientInstagram(), true));
	}

	/**
	 * Borrado manual e irreversible de historial ("Última hora"/"Últimas 24 horas"/"Últimas 4
	 * semanas"/"Historial entero") desde Turnos → Lista — solo dueño/admin, a diferencia del resto
	 * de esta clase (STAFF no puede purgar historial). Ver AppointmentService#purgeHistory: la
	 * ventana nunca incluye turnos futuros, sin importar qué window se elija.
	 */
	@DeleteMapping("/history")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public HistoryPurgeResponse purgeHistory(@RequestParam HistoryWindow window) {
		return new HistoryPurgeResponse(appointmentService.purgeHistory(window));
	}

	/**
	 * Borrado manual e irreversible de un turno puntual — mismo espíritu que {@link #purgeHistory}
	 * (solo dueño/admin, no toca la calificación del cliente) pero sin restricción de fecha: para
	 * sacarse de encima un turno de prueba o cargado por error, sea pasado o futuro.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public void delete(@PathVariable UUID id) {
		appointmentService.delete(id);
	}

	private Instant toInstant(BookAppointmentRequest request) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		return ZonedDateTime.of(request.date(), request.startTime(), zone).toInstant();
	}

	private Instant toInstant(RescheduleRequest request) {
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
