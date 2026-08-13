package dev.capibyte.bookingsaas.admin;

import dev.capibyte.bookingsaas.admin.dto.AdminSupportReportResponse;
import dev.capibyte.bookingsaas.admin.dto.AdminTenantSummaryResponse;
import dev.capibyte.bookingsaas.admin.dto.MrrSummaryResponse;
import dev.capibyte.bookingsaas.admin.dto.PricingReferenceResponse;
import dev.capibyte.bookingsaas.admin.dto.TenantDetailResponse;
import dev.capibyte.bookingsaas.admin.dto.TenantUsageResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingRepository;
import dev.capibyte.bookingsaas.common.FileStorageService;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.AppUserService;
import dev.capibyte.bookingsaas.notification.MailService;
import dev.capibyte.bookingsaas.staff.ProfessionalRepository;
import dev.capibyte.bookingsaas.support.SupportReportPriority;
import dev.capibyte.bookingsaas.tenant.BranchRepository;
import dev.capibyte.bookingsaas.tenant.PlanPricingService;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.PlatformSettingsRepository;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import dev.capibyte.bookingsaas.tenant.TenantStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
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

	private static final int USAGE_WINDOW_DAYS = 30;

	private final TenantRepository tenantRepository;
	private final PlatformAdminRepository platformAdminRepository;
	private final FileStorageService fileStorageService;
	private final AppUserService appUserService;
	private final MailService mailService;
	private final BranchRepository branchRepository;
	private final ProfessionalRepository professionalRepository;
	private final ServiceOfferingRepository serviceOfferingRepository;
	private final PlanPricingService planPricingService;
	private final PlatformSettingsRepository platformSettingsRepository;

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
						row.tenantSlug(), row.submitterEmail(), row.type(), row.priority(), row.message(),
						row.hasImage(), row.resolved(), row.createdAt()))
				.toList();
	}

	public void resolveReport(UUID id, boolean resolved) {
		platformAdminRepository.markResolved(id, resolved);
	}

	public void updateReportPriority(UUID id, SupportReportPriority priority) {
		platformAdminRepository.updatePriority(id, priority.name());
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

	/** Purely a reporting figure (see Tenant#customMonthlyPrice) — never touches what
	 * SubscriptionService actually charges via MercadoPago. */
	@Transactional
	public AdminTenantSummaryResponse updateTenantCustomPrice(UUID tenantId, BigDecimal customMonthlyPrice) {
		Tenant tenant = findTenant(tenantId);
		tenant.setCustomMonthlyPrice(customMonthlyPrice);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null),
				platformAdminRepository.countProfessionalsForTenant(tenantId));
	}

	/** Set by hand when approving a "mejorar plan" request for more employees within the same
	 * tier — not self-service, see PlanTier's Javadoc. Null clears the override, falling back to
	 * the plan's default included count. */
	@Transactional
	public AdminTenantSummaryResponse updateTenantProfessionalLimit(UUID tenantId, Integer professionalLimitOverride) {
		Tenant tenant = findTenant(tenantId);
		tenant.setProfessionalLimitOverride(professionalLimitOverride);
		return toSummary(tenant, platformAdminRepository.findLatestSubscriptionStatus(tenantId).orElse(null),
				platformAdminRepository.countProfessionalsForTenant(tenantId));
	}

	/** For the admin manual's "referencia del dólar blue" section — see PlanPricingScheduler for
	 * how/when this moves. */
	@Transactional(readOnly = true)
	public PricingReferenceResponse pricingReference() {
		var reference = platformSettingsRepository.getReference();
		return new PricingReferenceResponse(reference.referenceBlueRate(), reference.updatedAt());
	}

	/** Only ACTIVE tenants count — a suspended or pending-approval tenant isn't actually being
	 * billed. Tenants on a not-yet-priced tier (see PlanTier#getMonthlyPrice) contribute zero
	 * rather than blowing up the sum. */
	@Transactional(readOnly = true)
	public MrrSummaryResponse mrrSummary() {
		Map<PlanTier, BigDecimal> mrrByPlan = new EnumMap<>(PlanTier.class);
		Map<PlanTier, Long> tenantCountByPlan = new EnumMap<>(PlanTier.class);
		BigDecimal total = BigDecimal.ZERO;
		for (Tenant tenant : tenantRepository.findAll()) {
			if (tenant.getStatus() != TenantStatus.ACTIVE) {
				continue;
			}
			BigDecimal price = planPricingService.effectivePrice(tenant);
			if (price == null) {
				price = BigDecimal.ZERO;
			}
			mrrByPlan.merge(tenant.getPlanTier(), price, BigDecimal::add);
			tenantCountByPlan.merge(tenant.getPlanTier(), 1L, Long::sum);
			total = total.add(price);
		}
		return new MrrSummaryResponse(total, mrrByPlan, tenantCountByPlan);
	}

	/** Every tenant, ranked by revenue over the last {@value #USAGE_WINDOW_DAYS} days — tenants
	 * with no completed appointments in the window show up with zero, rather than being omitted,
	 * so the ranking is a complete picture, not just the tenants that happened to have activity. */
	@Transactional(readOnly = true)
	public List<TenantUsageResponse> tenantUsageRanking() {
		Instant to = Instant.now();
		Instant from = to.minus(USAGE_WINDOW_DAYS, ChronoUnit.DAYS);
		Map<UUID, PlatformAdminRepository.UsageRow> usageByTenant = platformAdminRepository.findTenantUsage(from, to)
				.stream()
				.collect(java.util.stream.Collectors.toMap(PlatformAdminRepository.UsageRow::tenantId, row -> row));
		Map<UUID, Long> professionalCountByTenant = platformAdminRepository.countProfessionalsByTenant();
		Map<UUID, Long> branchCountByTenant = platformAdminRepository.countBranchesByTenant();
		Map<UUID, Long> serviceCountByTenant = platformAdminRepository.countServicesByTenant();
		Map<UUID, Long> stockByTenant = platformAdminRepository.sumStockByTenant();
		return tenantRepository.findAll().stream()
				.map(tenant -> {
					var usage = usageByTenant.get(tenant.getId());
					long appointmentCount = usage == null ? 0 : usage.appointmentCount();
					BigDecimal revenue = usage == null || usage.revenue() == null ? BigDecimal.ZERO : usage.revenue();
					return new TenantUsageResponse(tenant.getId(), tenant.getName(), tenant.getPlanTier(),
							professionalCountByTenant.getOrDefault(tenant.getId(), 0L),
							branchCountByTenant.getOrDefault(tenant.getId(), 0L),
							stockByTenant.getOrDefault(tenant.getId(), 0L),
							serviceCountByTenant.getOrDefault(tenant.getId(), 0L),
							appointmentCount, revenue);
				})
				.sorted(Comparator.comparing(TenantUsageResponse::revenue).reversed())
				.toList();
	}

	/** CSV, not Excel — no spreadsheet library in this codebase yet, and the volume of rows doesn't
	 * justify adding one; a CSV opens fine in Excel/Sheets either way. {@code to} is inclusive. */
	@Transactional(readOnly = true)
	public byte[] billingReportCsv(LocalDate from, LocalDate to) {
		StringBuilder csv = new StringBuilder("Tenant,Plan,Monto,Estado,Fecha\n");
		for (var row : platformAdminRepository.findSubscriptionPayments(from, to)) {
			csv.append(csvField(row.tenantName())).append(',')
					.append(csvField(row.planTier())).append(',')
					.append(row.amount()).append(',')
					.append(csvField(row.status())).append(',')
					.append(row.createdAt().atZone(ZoneOffset.UTC).toLocalDate())
					.append('\n');
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String csvField(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	/** Read-only cross-section of a tenant's own data — reuses the same tenant-scoped repositories
	 * the owner's own panel uses, just with {@link TenantContext} set for this one tenant instead
	 * of resolved from the caller's session (same pattern approveTenant already uses to touch a
	 * single tenant's data), so the super-admin never needs the owner's own login to look. */
	@Transactional(readOnly = true)
	public TenantDetailResponse tenantDetail(UUID tenantId) {
		findTenant(tenantId);
		TenantContext.setTenantId(tenantId);
		try {
			List<TenantDetailResponse.BranchInfo> branches = branchRepository.findAll().stream()
					.map(b -> new TenantDetailResponse.BranchInfo(b.getName(), b.getAddress(), b.isActive()))
					.toList();
			List<TenantDetailResponse.ProfessionalInfo> professionals = professionalRepository.findAll().stream()
					.map(p -> new TenantDetailResponse.ProfessionalInfo(p.getDisplayName(), p.isActive()))
					.toList();
			List<TenantDetailResponse.ServiceInfo> services = serviceOfferingRepository.findAll().stream()
					.map(s -> new TenantDetailResponse.ServiceInfo(s.getName(), s.getPrice(), s.isActive()))
					.toList();
			return new TenantDetailResponse(branches, professionals, services);
		} finally {
			TenantContext.clear();
		}
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
				professionalCount, tenant.getCustomMonthlyPrice(), planPricingService.effectivePrice(tenant),
				tenant.getProfessionalLimitOverride(), tenant.getEffectiveProfessionalLimit());
	}
}
