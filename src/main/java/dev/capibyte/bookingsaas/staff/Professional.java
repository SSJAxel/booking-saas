package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "professionals")
@Getter
@Setter
@NoArgsConstructor
public class Professional extends BaseTenantEntity {

	@Column(name = "branch_id", nullable = false)
	private UUID branchId;

	@Column(name = "app_user_id")
	private UUID appUserId;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column
	private String bio;

	@Column(nullable = false)
	private boolean active = true;
}
