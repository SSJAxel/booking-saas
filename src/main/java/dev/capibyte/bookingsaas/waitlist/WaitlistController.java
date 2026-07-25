package dev.capibyte.bookingsaas.waitlist;

import dev.capibyte.bookingsaas.waitlist.dto.JoinWaitlistRequest;
import dev.capibyte.bookingsaas.waitlist.dto.WaitlistEntryResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WaitlistController {

	private final WaitlistService waitlistService;

	/** Public — same tenant resolution as the rest of /api/public/{tenantSlug}/**. */
	@PostMapping("/api/public/{tenantSlug}/waitlist")
	@ResponseStatus(HttpStatus.CREATED)
	public WaitlistEntryResponse join(@PathVariable String tenantSlug, @Valid @RequestBody JoinWaitlistRequest request) {
		return WaitlistEntryResponse.from(waitlistService.join(request.professionalId(), request.serviceId(),
				request.date(), request.clientName(), request.clientEmail(), request.clientPhone()));
	}

	@GetMapping("/api/waitlist")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
	public List<WaitlistEntryResponse> list(
			@RequestParam(required = false) UUID professionalId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) WaitlistStatus status) {
		return waitlistService.search(professionalId, date, status).stream().map(WaitlistEntryResponse::from).toList();
	}
}
