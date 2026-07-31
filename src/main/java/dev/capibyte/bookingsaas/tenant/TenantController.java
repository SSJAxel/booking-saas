package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.dto.PlanChangeRequest;
import dev.capibyte.bookingsaas.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service settings for the caller's own tenant — the tenant-level counterpart to /api/me. */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;

	@GetMapping
	@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
	public TenantResponse get() {
		return TenantResponse.from(tenantService.findById(TenantContext.getTenantId()));
	}

	/** Owner-only: this is the closest thing to a billing action this MVP has. */
	@PatchMapping("/plan")
	@PreAuthorize("hasRole('OWNER')")
	public TenantResponse changePlan(@Valid @RequestBody PlanChangeRequest request) {
		return TenantResponse.from(tenantService.changePlan(TenantContext.getTenantId(), request.planTier()));
	}
}
