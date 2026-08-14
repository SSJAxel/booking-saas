package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant itself is NOT tenant-scoped (there's no outer scope to filter it by) —
 * it's the root every {@link dev.capibyte.bookingsaas.common.BaseTenantEntity} hangs off of.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private String timezone = "UTC";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenantStatus status = TenantStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_tier", nullable = false)
	private PlanTier planTier = PlanTier.TRIAL;

	/** All optional — an unbranded tenant just gets frontend-public's default look. */
	@Column(name = "logo_url")
	private String logoUrl;

	/** Cover photo for the public booking page hero, shown behind/alongside the logo — same
	 * URL-string convention as logoUrl (see V10's Javadoc). */
	@Column(name = "banner_url")
	private String bannerUrl;

	@Column(name = "accent_color")
	private String accentColor;

	@Column
	private String tagline;

	/** All optional — the public site's side menu only shows an icon for whichever of these are
	 * set. instagramFeedUrl is a third-party embed-widget <script> src (Trustindex/SnapWidget/
	 * Elfsight/etc, tenant's own choice of provider) — distinct from instagramUrl (a plain profile
	 * link), not the same field doing double duty. */
	@Column(name = "instagram_url")
	private String instagramUrl;

	@Column(name = "facebook_url")
	private String facebookUrl;

	@Column(name = "instagram_feed_url")
	private String instagramFeedUrl;

	/** Off by default — an extra channel alongside email, never a replacement for it. */
	@Column(name = "whatsapp_enabled", nullable = false)
	private boolean whatsappEnabled = false;

	/** Business contact channels behind the owner's quick-access buttons — both optional. */
	@Column(name = "contact_email")
	private String contactEmail;

	@Column(name = "whatsapp_number")
	private String whatsappNumber;

	/** Shown to a client on the public booking page when the chosen service requires a deposit —
	 * an alternative to the MercadoPago checkout, confirmed manually by the owner (see
	 * AppointmentController.confirmDeposit). Optional. */
	@Column(name = "transfer_alias")
	private String transferAlias;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Manually maintained by the founder when a payment is collected — NOT computed from
	 * MercadoPago webhooks (see PlatformAdminService). Null means no due date has been set yet. */
	@Column(name = "next_payment_due_at")
	private LocalDate nextPaymentDueAt;

	/** Minimum ClientRatingService rating a client needs to clear to show up in the "mejores
	 * clientes" panel. */
	@Column(name = "top_clients_threshold", nullable = false)
	private int topClientsThreshold = 8;

	/** How many clients the "mejores clientes" panel shows at most — capped at 15 by
	 * TenantService.updateClientRankingSettings, not just the DB check constraint, so a bad value
	 * fails with a clear message instead of a raw constraint-violation 500. */
	@Column(name = "top_clients_count", nullable = false)
	private int topClientsCount = 3;

	/** Cuántos minutos (10–180) tiene un depósito PENDING antes de que
	 * PendingDepositExpirationScheduler cancele el turno solo — solo se consulta para tenants con
	 * MercadoPago habilitado en su plan, el scheduler ignora por completo a los que no lo tienen
	 * (ver el Javadoc de esa clase). Rango acotado tanto acá como en el request DTO y con un CHECK
	 * de la base (V32) — mismo patrón de triple validación que topClientsCount. */
	@Column(name = "deposit_expiration_minutes", nullable = false)
	private int depositExpirationMinutes = 30;

	/** Cuántos meses de historial de turnos se conservan como máximo (1–12) —
	 * AppointmentRetentionScheduler borra (DELETE real, irreversible) los turnos con startTime más
	 * viejo que este límite, corriendo periódicamente para todos los tenants. Capeado en 12 tanto
	 * acá (V27 CHECK) como en TenantService.updateHistoryRetentionMonths, mismo patrón de triple
	 * validación que topClientsCount. */
	@Column(name = "history_retention_months", nullable = false)
	private int historyRetentionMonths = 12;

	/** Set once, at creation (TenantService.create) — null for every tenant that existed before
	 * this feature shipped, which is deliberate: TrialExpirationScheduler only ever acts on a
	 * non-null value in the past, so old TRIAL ("Demo") tenants keep working exactly as before,
	 * unaffected. */
	@Column(name = "trial_expires_at")
	private Instant trialExpiresAt;

	/** Negotiated price for this tenant, set by hand by the super-admin — overrides the plan
	 * tier's current list price (see {@code PlanPricingService#effectivePrice}) for MRR/billing
	 * reporting only. Never used to compute what MercadoPago actually charges at checkout
	 * (SubscriptionService keeps using the plan's list price there) — deliberately kept out of
	 * that flow to avoid touching live payment code. */
	@Column(name = "custom_monthly_price")
	private BigDecimal customMonthlyPrice;

	/** Founder-set override for how many professionals this tenant may have, independent of its
	 * plan tier's default included count — set by hand when approving a "mejorar plan" request
	 * for more employees within the same tier (not self-service, see PlanTier's Javadoc). Null
	 * falls back to the plan's default. */
	@Column(name = "professional_limit_override")
	private Integer professionalLimitOverride;

	/** Single source of truth for "how many professionals can this tenant have" — the override if
	 * the founder set one, otherwise the plan tier's default included count. */
	public int getEffectiveProfessionalLimit() {
		return professionalLimitOverride != null ? professionalLimitOverride : planTier.getMaxProfessionals();
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
