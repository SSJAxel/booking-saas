package dev.capibyte.bookingsaas.payment;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentService;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.common.UnauthorizedException;
import dev.capibyte.bookingsaas.payment.dto.CheckoutResponse;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentRepository paymentRepository;
	private final AppointmentService appointmentService;
	private final ServiceOfferingService serviceOfferingService;
	private final MercadoPagoClient mercadoPagoClient;
	private final MercadoPagoAccountService mercadoPagoAccountService;
	private final WebhookSignatureVerifier signatureVerifier;
	private final TenantService tenantService;
	private final String webhookSecret;

	public PaymentService(PaymentRepository paymentRepository, AppointmentService appointmentService,
			ServiceOfferingService serviceOfferingService, MercadoPagoClient mercadoPagoClient,
			MercadoPagoAccountService mercadoPagoAccountService, WebhookSignatureVerifier signatureVerifier,
			TenantService tenantService, @Value("${app.mercadopago.webhook-secret}") String webhookSecret) {
		this.paymentRepository = paymentRepository;
		this.appointmentService = appointmentService;
		this.serviceOfferingService = serviceOfferingService;
		this.mercadoPagoClient = mercadoPagoClient;
		this.mercadoPagoAccountService = mercadoPagoAccountService;
		this.signatureVerifier = signatureVerifier;
		this.tenantService = tenantService;
		this.webhookSecret = webhookSecret;
	}

	/**
	 * Called from the public booking API, where {@link TenantContext} is already set by
	 * PublicTenantResolutionFilter before this runs — no session-timing concern here (contrast
	 * with {@link #handleWebhook}, which has to set it up itself).
	 */
	@Transactional
	public CheckoutResponse createCheckout(UUID appointmentId) {
		Appointment appointment = appointmentService.findById(appointmentId);
		if (appointment.getPaymentStatus() != PaymentStatus.PENDING) {
			throw new BadRequestException(
					"This appointment doesn't require a deposit, or its deposit was already handled");
		}
		UUID tenantId = TenantContext.getTenantId();
		Tenant tenant = tenantService.findById(tenantId);
		PlanTier tier = tenant.getPlanTier();
		if (!tier.isMercadoPagoEnabled()) {
			throw new BadRequestException("Plan " + tier + " doesn't include Mercado Pago deposits — "
					+ "confirm the deposit manually instead (transfer + confirm-deposit)");
		}
		ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
		// Normally the service's own depositAmount — overridden only when a ServiceCombo assigned the
		// whole combined deposit to this leg (see AppointmentService#bookGroup); the other leg in that
		// case never reaches here at all, since its paymentStatus is NOT_REQUIRED.
		BigDecimal depositAmount = appointment.getDepositAmountOverride() != null
				? appointment.getDepositAmountOverride() : service.getDepositAmount();
		// Mercado Pago's own cut is never on the house — when the tenant told us their real commission
		// %, the CLIENT pays deposit + that %, not the tenant, so the tenant's net for this booking
		// still matches the seña they configured. Only applies to this checkout, never the transfer
		// alias (no processing fee on a direct bank transfer) — see Tenant.mercadoPagoFeePercent.
		BigDecimal chargedAmount = tenant.getMercadoPagoFeePercent() != null
				? depositAmount
						.multiply(BigDecimal.ONE.add(tenant.getMercadoPagoFeePercent().movePointLeft(2)))
						.setScale(2, RoundingMode.HALF_UP)
				: depositAmount;
		String description = appointment.getDepositAmountOverride() != null ? "Deposit for " + service.getName()
				+ " (combo)" : "Deposit for " + service.getName();

		Payment payment = new Payment();
		payment.setAppointmentId(appointmentId);
		payment.setAmount(chargedAmount);
		payment.setStatus(PaymentStatus.PENDING);
		payment = paymentRepository.save(payment);

		String accessToken = mercadoPagoAccountService.resolveAccessToken(tenantId);
		MercadoPagoPreference preference = mercadoPagoClient.createPreference(accessToken, tenantId, payment.getId(),
				description, chargedAmount);
		payment.setProviderPreferenceId(preference.id());
		// Diagnostic for the "Unknown business: undefined" checkout report (2026-08-19) — the
		// preference id/init_point aren't secret (they're already returned to the client), logging
		// them lets us compare this real request's preference against a known-good one created
		// manually with the same tenant's token.
		log.info(
				"createCheckout appointment={} tenant={} depositAmount={} chargedAmount={} description={} -> preferenceId={} initPoint={}",
				appointmentId, tenantId, depositAmount, chargedAmount, description, preference.id(),
				preference.initPoint());

		return new CheckoutResponse(payment.getId(), preference.initPoint());
	}

	/**
	 * Deliberately NOT @Transactional at this level — same reasoning as AuthService.register()/
	 * login(): the tenant isn't known (there's no JWT or URL slug here) until after fetching the
	 * payment from MercadoPago and parsing its external_reference, so TenantContext has to be set
	 * before any @Transactional method that touches a tenant-scoped repository runs, not partway
	 * through one. For the same reason this can't call a private/protected @Transactional helper
	 * method on itself either — a same-bean self-invocation bypasses Spring's proxy entirely, so
	 * the annotation would be silently ignored. Instead it calls straight through to
	 * paymentRepository (each JpaRepository method is already its own transactional proxy call)
	 * and appointmentService (a different bean, so its @Transactional methods proxy correctly).
	 *
	 * <p>The re-fetch below uses the platform's own token, not a resolved-per-tenant one — the
	 * tenant isn't known yet at this point (that's the whole reason for the re-fetch), and
	 * MercadoPago's marketplace model gives the integrating application read access to payments it
	 * created through a connected account's OAuth flow. Unverified against a live sandbox, same
	 * disclaimer as the rest of this integration (see README).
	 */
	public void handleWebhook(String dataId, String requestId, String signatureHeader) {
		if (!signatureVerifier.isValid(dataId, requestId, signatureHeader, webhookSecret)) {
			throw new UnauthorizedException("Invalid MercadoPago webhook signature");
		}

		MercadoPagoPayment mpPayment = mercadoPagoClient.getPayment(mercadoPagoAccountService.getPlatformAccessToken(),
				dataId);
		String[] reference = parseExternalReference(mpPayment.externalReference());
		UUID tenantId = UUID.fromString(reference[0]);
		UUID paymentId = UUID.fromString(reference[1]);

		TenantContext.setTenantId(tenantId);
		try {
			Payment payment = paymentRepository.findById(paymentId)
					.orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));

			PaymentStatus mappedStatus = mapStatus(mpPayment.status());
			payment.setProviderPaymentId(mpPayment.id());
			payment.setStatus(mappedStatus);
			paymentRepository.save(payment);

			if (mappedStatus == PaymentStatus.PAID) {
				appointmentService.markDepositPaid(payment.getAppointmentId());
			}
		} finally {
			TenantContext.clear();
		}
	}

	private String[] parseExternalReference(String externalReference) {
		String[] parts = externalReference.split(":", 2);
		if (parts.length != 2) {
			throw new BadRequestException("Malformed external_reference from MercadoPago: " + externalReference);
		}
		return parts;
	}

	private PaymentStatus mapStatus(String mercadoPagoStatus) {
		return switch (mercadoPagoStatus) {
			case "approved" -> PaymentStatus.PAID;
			case "refunded", "charged_back" -> PaymentStatus.REFUNDED;
			case "rejected", "cancelled" -> PaymentStatus.FAILED;
			default -> PaymentStatus.PENDING; // pending, in_process, authorized, in_mediation
		};
	}
}
