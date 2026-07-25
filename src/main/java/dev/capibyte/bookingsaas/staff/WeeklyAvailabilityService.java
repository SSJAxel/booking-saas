package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeeklyAvailabilityService {

	private final WeeklyAvailabilityRepository weeklyAvailabilityRepository;
	private final ProfessionalService professionalService;

	@Transactional
	public WeeklyAvailability create(UUID professionalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		professionalService.findById(professionalId);
		if (!startTime.isBefore(endTime)) {
			throw new BadRequestException("startTime must be before endTime");
		}
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setProfessionalId(professionalId);
		availability.setDayOfWeek(dayOfWeek);
		availability.setStartTime(startTime);
		availability.setEndTime(endTime);
		return weeklyAvailabilityRepository.save(availability);
	}

	@Transactional(readOnly = true)
	public List<WeeklyAvailability> findByProfessional(UUID professionalId) {
		professionalService.findById(professionalId);
		return weeklyAvailabilityRepository.findAllByProfessionalId(professionalId);
	}

	@Transactional(readOnly = true)
	public List<WeeklyAvailability> findByProfessionalAndDay(UUID professionalId, DayOfWeek dayOfWeek) {
		return weeklyAvailabilityRepository.findAllByProfessionalIdAndDayOfWeek(professionalId, dayOfWeek);
	}

	@Transactional
	public void delete(UUID id) {
		if (!weeklyAvailabilityRepository.existsById(id)) {
			throw new NotFoundException("Availability slot not found: " + id);
		}
		weeklyAvailabilityRepository.deleteById(id);
	}
}
