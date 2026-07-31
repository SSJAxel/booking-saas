package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.AppointmentResponse;
import dev.capibyte.bookingsaas.booking.dto.StatusTransitionRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class AppointmentController {

	private final AppointmentService appointmentService;

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

	@PatchMapping("/{id}/status")
	public AppointmentResponse transition(@PathVariable UUID id, @Valid @RequestBody StatusTransitionRequest request) {
		return toResponse(appointmentService.transitionStatus(id, request.status()));
	}

	private AppointmentResponse toResponse(Appointment appointment) {
		return AppointmentResponse.from(appointment, appointmentService.findClient(appointment.getClientId()));
	}
}
