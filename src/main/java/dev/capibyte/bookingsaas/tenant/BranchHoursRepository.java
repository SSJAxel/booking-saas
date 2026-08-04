package dev.capibyte.bookingsaas.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchHoursRepository extends JpaRepository<BranchHours, UUID> {

	List<BranchHours> findAllByBranchId(UUID branchId);
}
