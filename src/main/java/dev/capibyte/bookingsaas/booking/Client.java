package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clients", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseTenantEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Column
	private String phone;

	@Column
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
