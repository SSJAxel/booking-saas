package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which professionals can perform which services. */
@Entity
@Table(name = "professional_service_assignments",
		uniqueConstraints = @UniqueConstraint(columnNames = {"professional_id", "service_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProfessionalServiceAssignment extends BaseTenantEntity {

	@Column(name = "professional_id", nullable = false)
	private UUID professionalId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;
}
