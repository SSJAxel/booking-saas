package dev.capibyte.bookingsaas.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

	List<Professional> findAllByBranchId(UUID branchId);
}
