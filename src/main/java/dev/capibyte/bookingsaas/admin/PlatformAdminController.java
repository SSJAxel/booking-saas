package dev.capibyte.bookingsaas.admin;

import dev.capibyte.bookingsaas.admin.dto.AdminSupportReportResponse;
import dev.capibyte.bookingsaas.admin.dto.AdminTenantSummaryResponse;
import dev.capibyte.bookingsaas.admin.dto.MrrSummaryResponse;
import dev.capibyte.bookingsaas.admin.dto.PricingReferenceResponse;
import dev.capibyte.bookingsaas.admin.dto.ResolveReportRequest;
import dev.capibyte.bookingsaas.admin.dto.TenantDetailResponse;
import dev.capibyte.bookingsaas.admin.dto.TenantUsageResponse;
import dev.capibyte.bookingsaas.admin.dto.UpdateNextPaymentDueRequest;
import dev.capibyte.bookingsaas.admin.dto.UpdateReportPriorityRequest;
import dev.capibyte.bookingsaas.admin.dto.UpdateTenantCustomPriceRequest;
import dev.capibyte.bookingsaas.admin.dto.UpdateTenantPlanRequest;
import dev.capibyte.bookingsaas.admin.dto.UpdateTenantProfessionalLimitRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide super-admin panel — cross-tenant reads only the founder's own account can reach.
 * See PlatformAdminRepository for why these bypass the normal tenant-scoped query layer. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class PlatformAdminController {

	private final PlatformAdminService platformAdminService;

	@GetMapping("/tenants")
	public List<AdminTenantSummaryResponse> tenants() {
		return platformAdminService.listTenants();
	}

	@PatchMapping("/tenants/{id}/plan")
	public AdminTenantSummaryResponse updateTenantPlan(@PathVariable UUID id,
			@Valid @RequestBody UpdateTenantPlanRequest request) {
		return platformAdminService.updateTenantPlan(id, request.planTier());
	}

	/** Lets the public booking site go live for this tenant and emails its owner — see
	 * TenantStatus#PENDING_APPROVAL and PlatformAdminService#approveTenant. */
	@PostMapping("/tenants/{id}/approve")
	public AdminTenantSummaryResponse approveTenant(@PathVariable UUID id) {
		return platformAdminService.approveTenant(id);
	}

	@PatchMapping("/tenants/{id}/next-payment-due")
	public AdminTenantSummaryResponse updateNextPaymentDue(@PathVariable UUID id,
			@RequestBody UpdateNextPaymentDueRequest request) {
		return platformAdminService.updateNextPaymentDue(id, request.nextPaymentDueAt());
	}

	@PatchMapping("/tenants/{id}/custom-price")
	public AdminTenantSummaryResponse updateTenantCustomPrice(@PathVariable UUID id,
			@RequestBody UpdateTenantCustomPriceRequest request) {
		return platformAdminService.updateTenantCustomPrice(id, request.customMonthlyPrice());
	}

	@PatchMapping("/tenants/{id}/professional-limit")
	public AdminTenantSummaryResponse updateTenantProfessionalLimit(@PathVariable UUID id,
			@RequestBody UpdateTenantProfessionalLimitRequest request) {
		return platformAdminService.updateTenantProfessionalLimit(id, request.professionalLimitOverride());
	}

	@GetMapping("/tenants/{id}/detail")
	public TenantDetailResponse tenantDetail(@PathVariable UUID id) {
		return platformAdminService.tenantDetail(id);
	}

	@GetMapping("/tenants/usage")
	public List<TenantUsageResponse> tenantUsage() {
		return platformAdminService.tenantUsageRanking();
	}

	@GetMapping("/mrr")
	public MrrSummaryResponse mrr() {
		return platformAdminService.mrrSummary();
	}

	@GetMapping("/pricing-reference")
	public PricingReferenceResponse pricingReference() {
		return platformAdminService.pricingReference();
	}

	/** {@code to} is inclusive on the whole day. */
	@GetMapping("/billing-report")
	public ResponseEntity<byte[]> billingReport(@RequestParam LocalDate from, @RequestParam LocalDate to) {
		byte[] csv = platformAdminService.billingReportCsv(from, to);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv"))
				.header("Content-Disposition", "attachment; filename=\"facturacion.csv\"")
				.body(csv);
	}

	@GetMapping("/support-reports")
	public List<AdminSupportReportResponse> supportReports() {
		return platformAdminService.listSupportReports();
	}

	@PatchMapping("/support-reports/{id}/resolved")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resolveReport(@PathVariable UUID id, @RequestBody ResolveReportRequest request) {
		platformAdminService.resolveReport(id, request.resolved());
	}

	@PatchMapping("/support-reports/{id}/priority")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateReportPriority(@PathVariable UUID id, @Valid @RequestBody UpdateReportPriorityRequest request) {
		platformAdminService.updateReportPriority(id, request.priority());
	}

	/** Permanent delete — for a report that turns out not to be a real issue, so the (otherwise
	 * permanent) resolved history stays trustworthy. */
	@DeleteMapping("/support-reports/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteReport(@PathVariable UUID id) {
		platformAdminService.deleteSupportReport(id);
	}

	@GetMapping("/support-reports/{id}/image")
	public ResponseEntity<Resource> supportReportImage(@PathVariable UUID id) {
		var image = platformAdminService.loadSupportReportImage(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType()))
				.body(image.resource());
	}
}
