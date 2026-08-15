package dev.capibyte.bookingsaas.booking;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	List<Review> findAllByVisibleTrueOrderByCreatedAtDesc();

	List<Review> findAllByOrderByCreatedAtDesc();
}
