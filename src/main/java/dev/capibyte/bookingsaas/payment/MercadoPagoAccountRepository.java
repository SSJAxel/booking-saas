package dev.capibyte.bookingsaas.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MercadoPagoAccountRepository extends JpaRepository<MercadoPagoAccount, UUID> {

	/** At most one row per tenant (unique constraint) — this is just "the" account, if connected. */
	Optional<MercadoPagoAccount> findFirstByOrderByCreatedAtDesc();
}
