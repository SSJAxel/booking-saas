package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.staff.dto.DateAvailabilityRequest;
import dev.capibyte.bookingsaas.staff.dto.DateAvailabilityResponse;
import dev.capibyte.bookingsaas.staff.dto.TimeOffRequest;
import dev.capibyte.bookingsaas.staff.dto.TimeOffResponse;
import dev.capibyte.bookingsaas.staff.dto.WeeklyAvailabilityRequest;
import dev.capibyte.bookingsaas.staff.dto.WeeklyAvailabilityResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professionals/{professionalId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AvailabilityController {

	private final WeeklyAvailabilityService weeklyAvailabilityService;
	private final TimeOffService timeOffService;
	private final DateAvailabilityService dateAvailabilityService;

	@GetMapping("/availability")
	public List<WeeklyAvailabilityResponse> listAvailability(@PathVariable UUID professionalId) {
		return weeklyAvailabilityService.findByProfessional(professionalId).stream()
				.map(WeeklyAvailabilityResponse::from).toList();
	}

	@PostMapping("/availability")
	@ResponseStatus(HttpStatus.CREATED)
	public WeeklyAvailabilityResponse addAvailability(@PathVariable UUID professionalId,
			@Valid @RequestBody WeeklyAvailabilityRequest request) {
		return WeeklyAvailabilityResponse.from(weeklyAvailabilityService.create(professionalId, request.dayOfWeek(),
				request.startTime(), request.endTime()));
	}

	@DeleteMapping("/availability/{availabilityId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAvailability(@PathVariable UUID professionalId, @PathVariable UUID availabilityId) {
		weeklyAvailabilityService.delete(availabilityId);
	}

	@GetMapping("/time-off")
	public List<TimeOffResponse> listTimeOff(@PathVariable UUID professionalId) {
		return timeOffService.findByProfessional(professionalId).stream().map(TimeOffResponse::from).toList();
	}

	@PostMapping("/time-off")
	@ResponseStatus(HttpStatus.CREATED)
	public TimeOffResponse addTimeOff(@PathVariable UUID professionalId, @Valid @RequestBody TimeOffRequest request) {
		return TimeOffResponse.from(timeOffService.create(professionalId, request.date(), request.startTime(),
				request.endTime(), request.reason()));
	}

	@DeleteMapping("/time-off/{timeOffId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTimeOff(@PathVariable UUID professionalId, @PathVariable UUID timeOffId) {
		timeOffService.delete(timeOffId);
	}

	/** Opens a specific date regardless of its day of week — the inverse of time-off, for a
	 * professional who wants to release capacity one calendar date at a time instead of committing
	 * to a recurring weekly rule (see DateAvailability's Javadoc). */
	@GetMapping("/date-availability")
	public List<DateAvailabilityResponse> listDateAvailability(@PathVariable UUID professionalId) {
		return dateAvailabilityService.findByProfessional(professionalId).stream()
				.map(DateAvailabilityResponse::from).toList();
	}

	@PostMapping("/date-availability")
	@ResponseStatus(HttpStatus.CREATED)
	public DateAvailabilityResponse addDateAvailability(@PathVariable UUID professionalId,
			@Valid @RequestBody DateAvailabilityRequest request) {
		return DateAvailabilityResponse.from(
				dateAvailabilityService.create(professionalId, request.date(), request.startTime(), request.endTime()));
	}

	@DeleteMapping("/date-availability/{dateAvailabilityId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteDateAvailability(@PathVariable UUID professionalId, @PathVariable UUID dateAvailabilityId) {
		dateAvailabilityService.delete(dateAvailabilityId);
	}
}
