package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DateAvailabilityService {

	private final DateAvailabilityRepository dateAvailabilityRepository;
	private final ProfessionalService professionalService;

	@Transactional
	public DateAvailability create(UUID professionalId, LocalDate date, LocalTime startTime, LocalTime endTime) {
		professionalService.findById(professionalId);
		if (!startTime.isBefore(endTime)) {
			throw new BadRequestException("startTime must be before endTime");
		}
		DateAvailability availability = new DateAvailability();
		availability.setProfessionalId(professionalId);
		availability.setDate(date);
		availability.setStartTime(startTime);
		availability.setEndTime(endTime);
		return dateAvailabilityRepository.save(availability);
	}

	@Transactional(readOnly = true)
	public List<DateAvailability> findByProfessional(UUID professionalId) {
		professionalService.findById(professionalId);
		return dateAvailabilityRepository.findAllByProfessionalId(professionalId);
	}

	@Transactional(readOnly = true)
	public List<DateAvailability> findByProfessionalAndDate(UUID professionalId, LocalDate date) {
		return dateAvailabilityRepository.findAllByProfessionalIdAndDate(professionalId, date);
	}

	@Transactional
	public void delete(UUID id) {
		if (!dateAvailabilityRepository.existsById(id)) {
			throw new NotFoundException("Date availability not found: " + id);
		}
		dateAvailabilityRepository.deleteById(id);
	}
}
