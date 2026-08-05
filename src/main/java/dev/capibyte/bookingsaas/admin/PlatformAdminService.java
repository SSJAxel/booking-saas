package dev.capibyte.bookingsaas.admin;

import dev.capibyte.bookingsaas.admin.dto.AdminSupportReportResponse;
import dev.capibyte.bookingsaas.admin.dto.AdminTenantSummaryResponse;
import dev.capibyte.bookingsaas.common.FileStorageService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

	private final TenantRepository tenantRepository;
	private final PlatformAdminRepository platformAdminRepository;
	private final FileStorageService fileStorageService;

	@Transactional(readOnly = true)
	public List<AdminTenantSummaryResponse> listTenants() {
		Map<UUID, String> subscriptionStatusByTenant = platformAdminRepository.findLatestSubscriptionStatusByTenant();
		return tenantRepository.findAll().stream()
				.map(tenant -> toSummary(tenant, subscriptionStatusByTenant.get(tenant.getId())))
				.toList();
	}

	public List<AdminSupportReportResponse> listSupportReports() {
		return platformAdminRepository.findAllSupportReports().stream()
				.map(row -> new AdminSupportReportResponse(row.id(), row.tenantId(), row.tenantName(),
						row.tenantSlug(), row.submitterEmail(), row.message(), row.resolved(), row.createdAt()))
				.toList();
	}

	public void resolveReport(UUID id, boolean resolved) {
		platformAdminRepository.markResolved(id, resolved);
	}

	/** Unlike TenantService.changePlan (self-service, restricted to free tiers — a paid tier there
	 * requires an authorized MercadoPago subscription), this lets the founder set ANY tier by hand
	 * for any tenant — e.g. after a manual/off-platform payment (bank transfer, cash). */
	@Transactional
	public AdminTenantSummaryResponse updateTenantPlan(UUID tenantId, PlanTier planTier) {
		Tenant tenant = findTenant(tenantId);
		tenant.setPlanTier(planTier);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null));
	}

	@Transactional
	public AdminTenantSummaryResponse updateNextPaymentDue(UUID tenantId, LocalDate nextPaymentDueAt) {
		Tenant tenant = findTenant(tenantId);
		tenant.setNextPaymentDueAt(nextPaymentDueAt);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null));
	}

	private Tenant findTenant(UUID tenantId) {
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new NotFoundException("Tenant not found: " + tenantId));
	}

	public SupportReportImage loadSupportReportImage(UUID id) {
		var ref = platformAdminRepository.findSupportReportImage(id)
				.orElseThrow(() -> new NotFoundException("Support report not found: " + id));
		return new SupportReportImage(fileStorageService.loadAsResource(ref.imagePath()), ref.contentType());
	}

	public record SupportReportImage(Resource resource, String contentType) {
	}

	private AdminTenantSummaryResponse toSummary(Tenant tenant, String subscriptionStatus) {
		LocalDate dueDate = tenant.getNextPaymentDueAt();
		Long daysRemaining = dueDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
		return new AdminTenantSummaryResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
				tenant.getStatus(), tenant.getPlanTier(), subscriptionStatus, dueDate, daysRemaining);
	}
}
