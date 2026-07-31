package dev.capibyte.bookingsaas.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

	List<Sale> findAllByProductId(UUID productId);
}
