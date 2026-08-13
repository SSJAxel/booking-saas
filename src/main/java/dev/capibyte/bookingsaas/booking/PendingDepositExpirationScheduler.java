package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A PENDING appointment with a PENDING deposit is holding a slot (the EXCLUDE constraint treats
 * it as occupied) on the strength of a checkout the client may never finish. Left alone, an
 * abandoned checkout would squat that slot forever, so this periodically cancels ones that have
 * been waiting too long — freeing the slot, since the EXCLUDE constraint's WHERE clause excludes
 * CANCELLED rows. Calls {@link AppointmentService#expireForNonPayment} (not {@code
 * transitionStatus}) so the cancellation still fires the client email/waitlist notification but
 * is scored against the client's rating as an abandoned checkout, not a "communicated"
 * cancellation — see that method's Javadoc.
 *
 * <p><b>Only applies to tenants whose plan has MercadoPago enabled</b>
 * ({@link dev.capibyte.bookingsaas.tenant.PlanTier#isMercadoPagoEnabled()}). A tenant without it
 * can't create an MP checkout at all (see {@code PaymentController}) — their only deposit path is
 * the owner manually confirming a bank transfer against the tenant's {@code transferAlias}
 * (`AppointmentController#confirmDeposit`) whenever they happen to check, which can reasonably be
 * hours, not minutes. Auto-cancelling those on a fixed timer would fight the owner's own judgment
 * call instead of supporting it. MercadoPago-enabled tenants keep the automatic timer because an
 * MP checkout is a live session with a natural abandon point, and freeing the slot quickly matters
 * more there.
 *
 * <p>The cutoff itself is per-tenant ({@link Tenant#getDepositExpirationMinutes()}, 10–180 min,
 * owner-editable via {@code PATCH /api/tenant/deposit-expiration}), not a single platform-wide
 * value — a tenant that wants slots freed up fast sets it low, one that's fine holding a slot
 * longer sets it high, within those bounds.
 *
 * <p>Runs with no tenant already in {@link TenantContext} (there's no request, no JWT, no URL
 * slug), so it has to loop tenants explicitly and set the context itself for each one — same
 * reasoning as {@code PaymentService.handleWebhook}. {@code Tenant} itself isn't tenant-scoped
 * (see its Javadoc), so listing all of them needs no context to be set first.
 */
@Component
@RequiredArgsConstructor
public class PendingDepositExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(PendingDepositExpirationScheduler.class);

	private final TenantRepository tenantRepository;
	private final AppointmentRepository appointmentRepository;
	private final AppointmentService appointmentService;

	@Scheduled(fixedDelayString = "${app.booking.expiration-check-interval-ms:60000}")
	public void expireUnpaidAppointments() {
		for (Tenant tenant : tenantRepository.findAll()) {
			if (!tenant.getPlanTier().isMercadoPagoEnabled()) {
				continue;
			}
			Instant cutoff = Instant.now().minus(tenant.getDepositExpirationMinutes(), ChronoUnit.MINUTES);
			TenantContext.setTenantId(tenant.getId());
			try {
				List<Appointment> stale = appointmentRepository.findAllByStatusAndPaymentStatusAndCreatedAtBefore(
						AppointmentStatus.PENDING, PaymentStatus.PENDING, cutoff);
				for (Appointment appointment : stale) {
					log.info("Expiring appointment {} for tenant {}: deposit unpaid after {} minutes",
							appointment.getId(), tenant.getId(), tenant.getDepositExpirationMinutes());
					appointmentService.expireForNonPayment(appointment.getId());
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
