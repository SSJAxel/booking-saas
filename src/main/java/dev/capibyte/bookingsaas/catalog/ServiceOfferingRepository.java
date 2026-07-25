package dev.capibyte.bookingsaas.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {
}
