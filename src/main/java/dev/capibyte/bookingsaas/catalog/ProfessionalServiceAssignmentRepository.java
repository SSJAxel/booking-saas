package dev.capibyte.bookingsaas.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalServiceAssignmentRepository extends JpaRepository<ProfessionalServiceAssignment, UUID> {

	List<ProfessionalServiceAssignment> findAllByServiceId(UUID serviceId);

	List<ProfessionalServiceAssignment> findAllByProfessionalId(UUID professionalId);

	Optional<ProfessionalServiceAssignment> findByProfessionalIdAndServiceId(UUID professionalId, UUID serviceId);
}
