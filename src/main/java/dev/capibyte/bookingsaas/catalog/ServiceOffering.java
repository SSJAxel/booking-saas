package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Named ServiceOffering, not Service, to avoid colliding with the Spring @Service stereotype. */
@Entity
@Table(name = "service_offerings")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOffering extends BaseTenantEntity {

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	/** Null means no deposit required to book this service. */
	@Column(name = "deposit_amount", precision = 10, scale = 2)
	private BigDecimal depositAmount;

	@Column(nullable = false)
	private boolean active = true;
}
