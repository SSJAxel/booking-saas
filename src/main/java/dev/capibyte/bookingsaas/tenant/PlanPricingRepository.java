package dev.capibyte.bookingsaas.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPricingRepository extends JpaRepository<PlanPricing, PlanTier> {
}
