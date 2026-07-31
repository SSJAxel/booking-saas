package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.notification.AppointmentNotificationEvent;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import dev.capibyte.bookingsaas.waitlist.WaitlistService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

	private static final List<AppointmentStatus> INACTIVE_STATUSES =
			List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

	private final AppointmentRepository appointmentRepository;
	private final ClientRepository clientRepository;
	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;
	private final WaitlistService waitlistService;
	private final TenantService tenantService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Appointment book(UUID professionalId, UUID serviceId, Instant startTime, String clientName,
			String clientEmail, String clientPhone) {
		ServiceOffering service = serviceOfferingService.findById(serviceId);
		List<UUID> eligible = serviceOfferingService.findProfessionalIdsForService(serviceId);
		if (!eligible.contains(professionalId)) {
			throw new NotFoundException("This professional does not offer the requested service");
		}
		Professional professional = professionalService.findById(professionalId);
		Instant endTime = startTime.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);

		Client client = clientRepository.findByEmail(clientEmail).orElseGet(() -> {
			Client created = new Client();
			created.setName(clientName);
			created.setEmail(clientEmail);
			created.setPhone(clientPhone);
			return clientRepository.save(created);
		});

		Appointment appointment = new Appointment();
		appointment.setBranchId(professional.getBranchId());
		appointment.setProfessionalId(professionalId);
		appointment.setServiceId(serviceId);
		appointment.setClientId(client.getId());
		appointment.setStartTime(startTime);
		appointment.setEndTime(endTime);
		PaymentStatus paymentStatus = service.getDepositAmount() != null ? PaymentStatus.PENDING : PaymentStatus.NOT_REQUIRED;
		appointment.setPaymentStatus(paymentStatus);
		// No deposit to wait for, so there's nothing PENDING should mean here — confirm immediately
		// instead of leaving it stuck until a human clicks "confirm" for no reason.
		appointment.setStatus(paymentStatus == PaymentStatus.NOT_REQUIRED ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING);

		try {
			// saveAndFlush (not save): the EXCLUDE constraint violation only surfaces once the
			// INSERT actually runs, which Hibernate otherwise defers to transaction commit —
			// by then it's past this try/catch, so it must be forced here to translate it to 409.
			Appointment saved = appointmentRepository.saveAndFlush(appointment);
			publishNotification(saved, client, professional, service, saved.getStatus());
			return saved;
		} catch (DataIntegrityViolationException ex) {
			if (isDoubleBookingViolation(ex)) {
				throw new SlotAlreadyBookedException();
			}
			throw ex;
		} catch (CannotAcquireLockException ex) {
			// Under heavy concurrent contention for the exact same slot, Postgres's deadlock
			// detector can abort one of the competing transactions instead of cleanly returning
			// a constraint violation (verified: fires under ~10 simultaneous requests for the
			// same range). It only happens here because of contention on the exclusion
			// constraint's GiST index, so it means the same thing — the slot was just taken.
			throw new SlotAlreadyBookedException();
		}
	}

	private boolean isDoubleBookingViolation(DataIntegrityViolationException ex) {
		Throwable cause = ex.getMostSpecificCause();
		return cause.getMessage() != null && cause.getMessage().contains("no_double_booking");
	}

	/**
	 * {@code zone} is the tenant's own IANA zone (TenantService.getZoneId), not a fixed offset —
	 * "this day" has to mean the same thing here as it does to the weekly-availability hours this
	 * result gets checked against in PublicAvailabilityService, or a booking near midnight could be
	 * attributed to the wrong calendar day and fail to block the slot it actually occupies.
	 */
	@Transactional(readOnly = true)
	public List<Appointment> findActiveByProfessionalAndDay(UUID professionalId, LocalDate date, ZoneId zone) {
		Instant dayStart = date.atStartOfDay(zone).toInstant();
		Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
		return appointmentRepository
				.findAllByProfessionalIdAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusNotIn(professionalId,
						dayStart, dayEnd, INACTIVE_STATUSES);
	}

	@Transactional(readOnly = true)
	public List<Appointment> search(UUID branchId, UUID professionalId, Instant from, Instant to,
			AppointmentStatus status) {
		return appointmentRepository.findAll().stream()
				.filter(a -> branchId == null || a.getBranchId().equals(branchId))
				.filter(a -> professionalId == null || a.getProfessionalId().equals(professionalId))
				.filter(a -> from == null || !a.getStartTime().isBefore(from))
				.filter(a -> to == null || a.getStartTime().isBefore(to))
				.filter(a -> status == null || a.getStatus() == status)
				.toList();
	}

	@Transactional(readOnly = true)
	public Appointment findById(UUID id) {
		return appointmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
	}

	@Transactional
	public Appointment transitionStatus(UUID id, AppointmentStatus newStatus) {
		Appointment appointment = findById(id);
		validateTransition(appointment.getStatus(), newStatus);
		appointment.setStatus(newStatus);

		Client client = loadClient(appointment.getClientId());
		Professional professional = professionalService.findById(appointment.getProfessionalId());
		ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
		publishNotification(appointment, client, professional, service, newStatus);

		if (newStatus == AppointmentStatus.CANCELLED) {
			ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
			LocalDate freedDate = appointment.getStartTime().atZone(zone).toLocalDate();
			waitlistService.notifyNextForFreedSlot(appointment.getProfessionalId(), appointment.getServiceId(), freedDate);
		}

		return appointment;
	}

	/**
	 * Called by PaymentService once MercadoPago confirms a deposit was paid. Deliberately doesn't
	 * go through transitionStatus()/validateTransition() — this is a payment-status update that
	 * only incidentally also confirms the appointment, and should be a no-op (not an error) if the
	 * appointment already moved on (e.g. got cancelled while the payment was in flight).
	 */
	@Transactional
	public void markDepositPaid(UUID appointmentId) {
		Appointment appointment = findById(appointmentId);
		if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
			return; // already processed — webhooks can be delivered more than once
		}
		appointment.setPaymentStatus(PaymentStatus.PAID);

		if (appointment.getStatus() == AppointmentStatus.PENDING) {
			appointment.setStatus(AppointmentStatus.CONFIRMED);
			Client client = loadClient(appointment.getClientId());
			Professional professional = professionalService.findById(appointment.getProfessionalId());
			ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
			publishNotification(appointment, client, professional, service, AppointmentStatus.CONFIRMED);
		}
	}

	/** Public so controllers can enrich AppointmentResponse with client contact info. */
	@Transactional(readOnly = true)
	public Client findClient(UUID clientId) {
		return loadClient(clientId);
	}

	private Client loadClient(UUID clientId) {
		return clientRepository.findById(clientId)
				.orElseThrow(() -> new NotFoundException("Client not found: " + clientId));
	}

	private void publishNotification(Appointment appointment, Client client, Professional professional,
			ServiceOffering service, AppointmentStatus status) {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		ZoneId zone = ZoneId.of(tenant.getTimezone());
		eventPublisher.publishEvent(new AppointmentNotificationEvent(client.getEmail(), client.getName(),
				professional.getDisplayName(), service.getName(), appointment.getStartTime(), zone, status,
				tenant.isWhatsappEnabled(), client.getPhone()));
	}

	private void validateTransition(AppointmentStatus from, AppointmentStatus to) {
		boolean valid = switch (from) {
			case PENDING -> to == AppointmentStatus.CONFIRMED || to == AppointmentStatus.CANCELLED;
			case CONFIRMED -> to == AppointmentStatus.CANCELLED || to == AppointmentStatus.COMPLETED
					|| to == AppointmentStatus.NO_SHOW;
			case CANCELLED, COMPLETED, NO_SHOW -> false;
		};
		if (!valid) {
			throw new BadRequestException("Cannot transition appointment from " + from + " to " + to);
		}
	}
}
