package dev.capibyte.bookingsaas.admin;

import dev.capibyte.bookingsaas.admin.dto.AdminSupportReportResponse;
import dev.capibyte.bookingsaas.admin.dto.AdminTenantSummaryResponse;
import dev.capibyte.bookingsaas.common.FileStorageService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.AppUserService;
import dev.capibyte.bookingsaas.notification.MailService;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import dev.capibyte.bookingsaas.tenant.TenantStatus;
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
	private final AppUserService appUserService;
	private final MailService mailService;

	@Transactional(readOnly = true)
	public List<AdminTenantSummaryResponse> listTenants() {
		Map<UUID, String> subscriptionStatusByTenant = platformAdminRepository.findLatestSubscriptionStatusByTenant();
		Map<UUID, Long> professionalCountByTenant = platformAdminRepository.countProfessionalsByTenant();
		return tenantRepository.findAll().stream()
				.map(tenant -> toSummary(tenant, subscriptionStatusByTenant.get(tenant.getId()),
						professionalCountByTenant.getOrDefault(tenant.getId(), 0L)))
				.toList();
	}

	public List<AdminSupportReportResponse> listSupportReports() {
		return platformAdminRepository.findAllSupportReports().stream()
				.map(row -> new AdminSupportReportResponse(row.id(), row.tenantId(), row.tenantName(),
						row.tenantSlug(), row.submitterEmail(), row.type(), row.message(), row.hasImage(),
						row.resolved(), row.createdAt()))
				.toList();
	}

	public void resolveReport(UUID id, boolean resolved) {
		platformAdminRepository.markResolved(id, resolved);
	}

	/** See PlatformAdminRepository#deleteSupportReport for why this is permanent, not a soft
	 * delete — the resolved list is meant to be a reliable permanent history, so the only way to
	 * keep a false positive out of it is to actually remove the row. */
	public void deleteSupportReport(UUID id) {
		platformAdminRepository.deleteSupportReport(id);
	}

	/** Unlike TenantService.changePlan (self-service, restricted to free tiers — a paid tier there
	 * requires an authorized MercadoPago subscription), this lets the founder set ANY tier by hand
	 * for any tenant — e.g. after a manual/off-platform payment (bank transfer, cash). */
	@Transactional
	public AdminTenantSummaryResponse updateTenantPlan(UUID tenantId, PlanTier planTier) {
		Tenant tenant = findTenant(tenantId);
		tenant.setPlanTier(planTier);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null),
				platformAdminRepository.countProfessionalsForTenant(tenantId));
	}

	/**
	 * Deliberately NOT @Transactional at this level, same reasoning as AuthService.register:
	 * {@code appUserService.findOwner()} needs its own fresh Hibernate session opened AFTER
	 * {@link TenantContext} is set — {@code AppUser} is {@code @TenantId}-scoped, and that
	 * identifier resolves once per session at session-open time, so setting the context inside an
	 * already-open transaction is too late for that transaction's own queries (see
	 * AppUserService's Javadoc, and the same gotcha documented on TenantService). The status
	 * update below doesn't rely on this method's own transaction boundary for the same reason —
	 * {@code save} explicitly, rather than mutating and trusting dirty-checking, since there's no
	 * single enclosing @Transactional here to flush it.
	 *
	 * <p>Emails the owner directly (not via an AFTER_COMMIT event, unlike e.g.
	 * SupportReportNotificationListener): this method has no single enclosing transaction for
	 * {@code @TransactionalEventListener} to hook onto — first tried an event here and it was
	 * silently dropped (no active transaction when publishEvent() runs means Spring never invokes
	 * an AFTER_COMMIT listener at all, by default, no error either), same root cause
	 * AuthService.register's Javadoc already documents for its own founder-notification email.
	 */
	public AdminTenantSummaryResponse approveTenant(UUID tenantId) {
		Tenant tenant = findTenant(tenantId);
		tenant.setStatus(TenantStatus.ACTIVE);
		Tenant saved = tenantRepository.save(tenant);

		TenantContext.setTenantId(tenantId);
		try {
			appUserService.findOwner().ifPresent(owner -> mailService.send(owner.getEmail(),
					"¡Tu cuenta ya está habilitada!",
					"Hola,\n\n" + saved.getName() + " ya fue aprobado. Tu sitio público de reservas ya está activo:\n"
							+ saved.getSlug() + "\n\nGracias por sumarte."));
		} finally {
			TenantContext.clear();
		}

		return toSummary(saved, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null),
				platformAdminRepository.countProfessionalsForTenant(tenantId));
	}

	@Transactional
	public AdminTenantSummaryResponse updateNextPaymentDue(UUID tenantId, LocalDate nextPaymentDueAt) {
		Tenant tenant = findTenant(tenantId);
		tenant.setNextPaymentDueAt(nextPaymentDueAt);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null),
				platformAdminRepository.countProfessionalsForTenant(tenantId));
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

	private AdminTenantSummaryResponse toSummary(Tenant tenant, String subscriptionStatus, long professionalCount) {
		LocalDate dueDate = tenant.getNextPaymentDueAt();
		Long daysRemaining = dueDate == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
		return new AdminTenantSummaryResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
				tenant.getStatus(), tenant.getPlanTier(), subscriptionStatus, dueDate, daysRemaining,
				professionalCount);
	}
}
