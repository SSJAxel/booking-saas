package dev.capibyte.bookingsaas.inventory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	long countByActiveTrue();

	/**
	 * Atomic conditional decrement: the WHERE clause re-checks stock in the same statement as the
	 * write, so two concurrent sales racing for the last unit can't both read "stock available"
	 * before either commits. Returns 0 (no rows affected) when there isn't enough stock — the
	 * caller treats that as the rejection, not an exception thrown from a separate read.
	 */
	@Modifying
	@Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
	int decrementStockIfAvailable(@Param("id") UUID id, @Param("quantity") int quantity);
}
