package dev.capibyte.bookingsaas.tenant;

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
public class BranchHoursService {

	private final BranchHoursRepository branchHoursRepository;
	private final BranchService branchService;

	@Transactional
	public BranchHours create(UUID branchId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		branchService.findById(branchId); // 404s (not FK violation) if missing or belongs to another tenant
		if (!startTime.isBefore(endTime)) {
			throw new BadRequestException("startTime must be before endTime");
		}
		BranchHours hours = new BranchHours();
		hours.setBranchId(branchId);
		hours.setDayOfWeek(dayOfWeek);
		hours.setStartTime(startTime);
		hours.setEndTime(endTime);
		return branchHoursRepository.save(hours);
	}

	@Transactional(readOnly = true)
	public List<BranchHours> findByBranch(UUID branchId) {
		branchService.findById(branchId);
		return branchHoursRepository.findAllByBranchId(branchId);
	}

	@Transactional
	public void delete(UUID id) {
		if (!branchHoursRepository.existsById(id)) {
			throw new NotFoundException("Branch hours not found: " + id);
		}
		branchHoursRepository.deleteById(id);
	}
}
