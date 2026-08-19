package dev.capibyte.bookingsaas.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceComboRepository extends JpaRepository<ServiceCombo, UUID> {

	Optional<ServiceCombo> findByServiceAIdAndServiceBIdAndActiveTrue(UUID serviceAId, UUID serviceBId);
}
