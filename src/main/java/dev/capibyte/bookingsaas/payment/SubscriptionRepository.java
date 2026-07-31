package dev.capibyte.bookingsaas.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

	Optional<Subscription> findFirstByStatusInOrderByCreatedAtDesc(List<SubscriptionStatus> statuses);
}
