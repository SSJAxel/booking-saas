package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends BaseTenantEntity {

	@Column(nullable = false)
	private String name;

	@Column
	private String address;

	@Column
	private String phone;

	@Column(name = "google_business_url")
	private String googleBusinessUrl;

	@Column(nullable = false)
	private boolean active = true;
}
