package dev.capibyte.bookingsaas.support;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportReportRepository extends JpaRepository<SupportReport, UUID> {
}
