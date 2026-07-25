package dev.capibyte.bookingsaas.staff;

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
public class TimeOffService {

	private final TimeOffRepository timeOffRepository;
	private final ProfessionalService professionalService;

	@Transactional
	public TimeOff create(UUID professionalId, LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {
		professionalService.findById(professionalId);
		TimeOff timeOff = new TimeOff();
		timeOff.setProfessionalId(professionalId);
		timeOff.setDate(date);
		timeOff.setStartTime(startTime);
		timeOff.setEndTime(endTime);
		timeOff.setReason(reason);
		return timeOffRepository.save(timeOff);
	}

	@Transactional(readOnly = true)
	public List<TimeOff> findByProfessional(UUID professionalId) {
		professionalService.findById(professionalId);
		return timeOffRepository.findAllByProfessionalId(professionalId);
	}

	@Transactional(readOnly = true)
	public List<TimeOff> findByProfessionalAndDate(UUID professionalId, LocalDate date) {
		return timeOffRepository.findAllByProfessionalIdAndDate(professionalId, date);
	}

	@Transactional
	public void delete(UUID id) {
		if (!timeOffRepository.existsById(id)) {
			throw new NotFoundException("Time off not found: " + id);
		}
		timeOffRepository.deleteById(id);
	}
}
