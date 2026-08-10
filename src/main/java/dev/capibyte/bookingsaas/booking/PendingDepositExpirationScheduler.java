package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>Runs with no tenant already in {@link TenantContext} (there's no request, no JWT, no URL
 * slug), so it has to loop tenants explicitly and set the context itself for each one — same
 * reasoning as {@code PaymentService.handleWebhook}. {@code Tenant} itself isn't tenant-scoped
 * (see its Javadoc), so listing all of them needs no context to be set first.
 */
@Component
public class PendingDepositExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(PendingDepositExpirationScheduler.class);

	private final TenantRepository tenantRepository;
	private final AppointmentRepository appointmentRepository;
	private final AppointmentService appointmentService;
	private final long expirationMinutes;

	public PendingDepositExpirationScheduler(TenantRepository tenantRepository,
			AppointmentRepository appointmentRepository, AppointmentService appointmentService,
			@Value("${app.booking.deposit-expiration-minutes}") long expirationMinutes) {
		this.tenantRepository = tenantRepository;
		this.appointmentRepository = appointmentRepository;
		this.appointmentService = appointmentService;
		this.expirationMinutes = expirationMinutes;
	}

	@Scheduled(fixedDelayString = "${app.booking.expiration-check-interval-ms:60000}")
	public void expireUnpaidAppointments() {
		Instant cutoff = Instant.now().minus(expirationMinutes, ChronoUnit.MINUTES);
		for (Tenant tenant : tenantRepository.findAll()) {
			TenantContext.setTenantId(tenant.getId());
			try {
				List<Appointment> stale = appointmentRepository.findAllByStatusAndPaymentStatusAndCreatedAtBefore(
						AppointmentStatus.PENDING, PaymentStatus.PENDING, cutoff);
				for (Appointment appointment : stale) {
					log.info("Expiring appointment {} for tenant {}: deposit unpaid after {} minutes",
							appointment.getId(), tenant.getId(), expirationMinutes);
					appointmentService.expireForNonPayment(appointment.getId());
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
