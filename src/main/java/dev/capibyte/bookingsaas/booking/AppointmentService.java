package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.AppUserService;
import dev.capibyte.bookingsaas.notification.AppointmentNotificationEvent;
import dev.capibyte.bookingsaas.notification.OrphanedDepositPaymentEvent;
import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import dev.capibyte.bookingsaas.waitlist.WaitlistService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

	// Package-private (not private): PublicAvailabilityService reuses this exact list so both
	// classes agree on what counts as an "active" (still-blocking) appointment status, instead of
	// duplicating the CANCELLED/NO_SHOW literal in two places.
	static final List<AppointmentStatus> INACTIVE_STATUSES =
			List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

	private final AppointmentRepository appointmentRepository;
	private final ClientRepository clientRepository;
	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;
	private final WaitlistService waitlistService;
	private final TenantService tenantService;
	private final ClientRatingService clientRatingService;
	private final LoyaltyPointsService loyaltyPointsService;
	private final ReviewService reviewService;
	private final ApplicationEventPublisher eventPublisher;
	private final AppUserService appUserService;
	private final PublicAvailabilityService publicAvailabilityService;

	@Transactional
	public Appointment book(UUID professionalId, UUID serviceId, Instant startTime, String clientName,
			String clientEmail, String clientPhone, String clientInstagram) {
		return book(professionalId, serviceId, startTime, clientName, clientEmail, clientPhone, clientInstagram,
				false);
	}

	/**
	 * {@code overtime=true} is how the owner deliberately double-books a professional on purpose —
	 * see the V25 migration: an overtime appointment sits entirely outside the no_double_booking
	 * constraint, both as the new row being inserted and as an existing row a new insert is checked
	 * against. Never derived from client input: the only caller that can pass true is
	 * AppointmentController#createOvertime, which hardcodes it — BookAppointmentRequest (shared with
	 * the public booking controller) has no such field, by design.
	 */
	@Transactional
	public Appointment book(UUID professionalId, UUID serviceId, Instant startTime, String clientName,
			String clientEmail, String clientPhone, String clientInstagram, boolean overtime) {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		PlanTier tier = tenant.getPlanTier();
		ZoneId zone = ZoneId.of(tenant.getTimezone());
		LocalDate localDate = startTime.atZone(zone).toLocalDate();
		if (tier.getMaxAppointmentsPerWeek() != null) {
			// Locks the tenant row for the rest of this transaction so two concurrent bookings can't
			// both read the same stale weekly count and both squeak in past the cap — see
			// TenantService#findByIdForUpdate. Scoped to this block (not the tenant fetch above,
			// shared by every booking regardless of tier) since only PERSONAL ever reaches here.
			tenantService.findByIdForUpdate(tenant.getId());
			// minusDays sobre getDayOfWeek().getValue() en vez de LocalDate.with(DayOfWeek.MONDAY):
			// inequívoco, no depende de la semántica de TemporalAdjuster de DayOfWeek.
			LocalDate weekStart = localDate.minusDays(localDate.getDayOfWeek().getValue() - 1L);
			Instant weekFrom = weekStart.atStartOfDay(zone).toInstant();
			Instant weekTo = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant();
			long activeThisWeek = appointmentRepository
					.countByStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusNotIn(weekFrom, weekTo,
							INACTIVE_STATUSES);
			if (activeThisWeek >= tier.getMaxAppointmentsPerWeek()) {
				throw new WeeklyAppointmentLimitExceededException(tier);
			}
		}
		ServiceOffering service = serviceOfferingService.findById(serviceId);
		List<UUID> eligible = serviceOfferingService.findProfessionalIdsForService(serviceId);
		if (!eligible.contains(professionalId)) {
			throw new NotFoundException("This professional does not offer the requested service");
		}
		Professional professional = professionalService.findById(professionalId);
		Instant endTime = startTime.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);

		// "Sobreturno" is the owner's own deliberate override (see this method's Javadoc) and must
		// keep bypassing this exactly like it already bypasses the no_double_booking constraint —
		// every other caller (public booking, and the owner's regular manual "Nuevo turno") must
		// land on a time the professional actually made available: within WeeklyAvailability/
		// DateAvailability, not inside a TimeOff block, and not already occupied by another
		// appointment. computeSlotsIgnoringPast (not findFreeSlots) on purpose: the owner's manual
		// path is allowed to backdate a walk-in that already happened today, which findFreeSlots'
		// own "exclude past slots for today" filter would otherwise reject.
		if (!overtime) {
			LocalTime requestedLocalTime = startTime.atZone(zone).toLocalTime();
			boolean isFree = publicAvailabilityService.computeSlotsIgnoringPast(professionalId, serviceId, localDate)
					.stream()
					.anyMatch(slot -> slot.start().equals(requestedLocalTime));
			if (!isFree) {
				throw new SlotAlreadyBookedException();
			}
		}

		Client client = findOrCreateClient(clientName, clientEmail, clientPhone, clientInstagram);

		Appointment appointment = new Appointment();
		appointment.setBranchId(professional.getBranchId());
		appointment.setProfessionalId(professionalId);
		appointment.setServiceId(serviceId);
		appointment.setClientId(client.getId());
		appointment.setStartTime(startTime);
		appointment.setEndTime(endTime);
		appointment.setOvertime(overtime);
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

	/**
	 * Matches on email first, then phone (only if the email didn't already match) — the same
	 * person rebooking with a slightly different email but the same phone number must resolve to
	 * their existing {@link Client} row, not a fresh one, or their whole rating history would be
	 * silently lost and restarted from 0. Doesn't overwrite the matched client's stored details
	 * with whatever was typed this time (a name typo or a changed email on a later booking
	 * shouldn't clobber the identity a rating is already attached to) — {@code instagramHandle} is
	 * the one exception, backfilled only when the existing client doesn't have one yet.
	 */
	private Client findOrCreateClient(String name, String email, String phone, String instagram) {
		Optional<Client> existing = clientRepository.findByEmail(email);
		if (existing.isEmpty() && phone != null && !phone.isBlank()) {
			existing = clientRepository.findByPhone(phone).stream().findFirst();
		}
		if (existing.isPresent()) {
			Client client = existing.get();
			if (client.getInstagramHandle() == null && instagram != null && !instagram.isBlank()) {
				client.setInstagramHandle(instagram);
			}
			return client;
		}
		Client created = new Client();
		created.setName(name);
		created.setEmail(email);
		created.setPhone(phone);
		created.setInstagramHandle(instagram);
		return clientRepository.save(created);
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
		// Most recent first (by startTime) — the "Turnos" list is otherwise left in whatever order
		// the DB happens to return rows in, which reads as oldest-on-top, newest-on-bottom to anyone
		// scanning it.
		return appointmentRepository.findAll(Sort.by(Sort.Direction.DESC, "startTime")).stream()
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

		switch (newStatus) {
			case CANCELLED -> {
				ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
				LocalDate freedDate = appointment.getStartTime().atZone(zone).toLocalDate();
				waitlistService.notifyNextForFreedSlot(appointment.getProfessionalId(), appointment.getServiceId(), freedDate);
				clientRatingService.recordCancellation(client);
			}
			case COMPLETED -> {
				clientRatingService.recordCompleted(client);
				Tenant tenant = tenantService.findById(TenantContext.getTenantId());
				loyaltyPointsService.awardPointForCompletedVisit(tenant, client);
				reviewService.issueInviteIfEnabled(tenant, appointment, client, professional, service);
			}
			case NO_SHOW -> clientRatingService.recordNoShow(client);
			default -> {
			}
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
	public Appointment markDepositPaid(UUID appointmentId) {
		Appointment appointment = findById(appointmentId);
		if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
			return appointment; // already processed — webhooks (or a re-click) can arrive more than once
		}
		appointment.setPaymentStatus(PaymentStatus.PAID);

		if (appointment.getStatus() == AppointmentStatus.PENDING) {
			appointment.setStatus(AppointmentStatus.CONFIRMED);
			Client client = loadClient(appointment.getClientId());
			Professional professional = professionalService.findById(appointment.getProfessionalId());
			ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
			publishNotification(appointment, client, professional, service, AppointmentStatus.CONFIRMED);
		} else if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			notifyOwnerOfOrphanedPayment(appointment);
		}
		return appointment;
	}

	/**
	 * The deposit was confirmed after PendingDepositExpirationScheduler already cancelled this
	 * same appointment for non-payment — per this method's own Javadoc above, the slot is never
	 * un-cancelled here (someone else may already hold it). Automatically tells the tenant owner
	 * it happened, via OrphanedDepositPaymentListener, so a paid-but-cancelled appointment doesn't
	 * sit unnoticed — the owner decides from there whether the slot's still worth coordinating a
	 * new booking around; a paid deposit is never refunded through this platform, no matter the
	 * reason the appointment didn't happen (same policy as a no-show). Silently does nothing if
	 * the tenant somehow has no OWNER user (shouldn't happen in practice — every tenant gets one
	 * at registration).
	 */
	private void notifyOwnerOfOrphanedPayment(Appointment appointment) {
		appUserService.findOwner().ifPresent(owner -> {
			Client client = loadClient(appointment.getClientId());
			Tenant tenant = tenantService.findById(TenantContext.getTenantId());
			ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
			eventPublisher.publishEvent(new OrphanedDepositPaymentEvent(owner.getEmail(), tenant.getName(),
					client.getName(), client.getEmail(), service.getName(), service.getDepositAmount()));
		});
	}

	/**
	 * "Reagendar": moves {@code appointmentId} to a new date/time for the exact same client,
	 * professional and service — {@link dev.capibyte.bookingsaas.booking.dto.RescheduleRequest} has
	 * no fields for those, they never change here. Updates startTime/endTime in place, on the SAME
	 * row, instead of cancelling and re-booking: that way the no_double_booking EXCLUDE constraint
	 * runs against this UPDATE exactly as it would an INSERT, so the same guarantee against clashing
	 * with another appointment of the same professional still holds.
	 *
	 * <p>{@code reason} decides two independent things:
	 * <ul>
	 *   <li>Whether the client's rating takes a hit: {@link RescheduleReason#TENANT_DECISION} never
	 *   does; {@link RescheduleReason#CLIENT_NOTICE} follows the same first-time-free grace as a
	 *   cancellation, via {@link ClientRatingService#recordRescheduledByClient}, against its own
	 *   counter ({@link Client#getRescheduledCount()}), kept deliberately separate from
	 *   {@link Client#getCancelledCount()}.</li>
	 *   <li>Whether an already-PAID deposit survives the move: TENANT_DECISION always keeps it, no
	 *   matter how many times it happens. CLIENT_NOTICE only keeps it the first time this client
	 *   reschedules for that reason; from the second time on the deposit is forfeited — modeled as
	 *   resetting {@code paymentStatus} back to PENDING on this same appointment, since no refund
	 *   flow exists anywhere in this codebase (the tenant simply keeps what was already paid).</li>
	 * </ul>
	 * If forfeiting a deposit knocks the appointment out of CONFIRMED (i.e. it was CONFIRMED only
	 * because the deposit was PAID), its status drops back to PENDING too — mirroring exactly what
	 * {@link #book} does for a fresh booking whose deposit isn't resolved yet.
	 *
	 * <p>Deliberately does NOT check {@link PlanTier#getMaxAppointmentsPerWeek()} — out of scope by
	 * design: this only moves an appointment that already counted against some past week, it never
	 * creates a new one, so a PERSONAL tenant right at their weekly cap can still reschedule.
	 */
	@Transactional
	public Appointment reschedule(UUID appointmentId, Instant newStartTime, RescheduleReason reason) {
		Appointment appointment = findById(appointmentId);
		if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
			throw new BadRequestException("Cannot reschedule an appointment with status " + appointment.getStatus());
		}

		Client client = loadClient(appointment.getClientId());
		boolean keepsDeposit;
		if (reason == RescheduleReason.TENANT_DECISION) {
			keepsDeposit = true;
		} else {
			keepsDeposit = client.getRescheduledCount() == 0;
			clientRatingService.recordRescheduledByClient(client);
		}

		if (!keepsDeposit && appointment.getPaymentStatus() == PaymentStatus.PAID) {
			appointment.setPaymentStatus(PaymentStatus.PENDING);
			if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
				appointment.setStatus(AppointmentStatus.PENDING);
			}
		}

		Duration duration = Duration.between(appointment.getStartTime(), appointment.getEndTime());
		appointment.setStartTime(newStartTime);
		appointment.setEndTime(newStartTime.plus(duration));

		try {
			// saveAndFlush (not save): same reason as book() — the EXCLUDE constraint only fires once
			// this UPDATE actually runs, which must happen inside this try/catch to translate it to
			// 409 instead of surfacing as an unhandled error at commit time.
			Appointment saved = appointmentRepository.saveAndFlush(appointment);
			Professional professional = professionalService.findById(saved.getProfessionalId());
			ServiceOffering service = serviceOfferingService.findById(saved.getServiceId());
			publishNotification(saved, client, professional, service, saved.getStatus());
			return saved;
		} catch (DataIntegrityViolationException ex) {
			if (isDoubleBookingViolation(ex)) {
				throw new SlotAlreadyBookedException();
			}
			throw ex;
		} catch (CannotAcquireLockException ex) {
			throw new SlotAlreadyBookedException();
		}
	}

	/**
	 * Manual, inmediato — botones de Turnos → Lista (OWNER/ADMIN, ver
	 * AppointmentController#purgeHistory). Real DELETE, irreversible, no toca el Client asociado
	 * (rating/contadores quedan intactos, ver V27's ON DELETE CASCADE/SET NULL para payments/sales).
	 *
	 * <p>El límite superior es SIEMPRE {@code now}, sin excepción — incluido {@code ALL}, que no
	 * borra "todo" en sentido literal, sino todo lo que ya pasó (startTime en [EPOCH, now]). Un
	 * turno futuro nunca puede caer dentro de ese rango, así que nunca se borra con esta feature,
	 * sin importar qué botón se apriete.
	 */
	@Transactional
	public long purgeHistory(HistoryWindow window) {
		Instant now = Instant.now();
		Instant from = switch (window) {
			case LAST_HOUR -> now.minus(1, ChronoUnit.HOURS);
			case LAST_24_HOURS -> now.minus(24, ChronoUnit.HOURS);
			case LAST_4_WEEKS -> now.minus(28, ChronoUnit.DAYS);
			case ALL -> Instant.EPOCH;
		};
		return appointmentRepository.deleteByStartTimeBetween(from, now);
	}

	/**
	 * Borrado manual de UN turno puntual (botón "Eliminar turno" en Turnos → Lista/Calendario) — a
	 * diferencia de {@link #purgeHistory}, no está limitado a turnos ya pasados: sirve para sacarse
	 * de encima un turno de prueba o cargado por error, sin importar su fecha o estado. Mismo
	 * contrato que purgeHistory: no toca el Client asociado (rating/contadores intactos), y
	 * payments/sales del turno siguen las reglas de V27 (payments se borra en cascada, sales solo
	 * pierde la referencia).
	 */
	@Transactional
	public void delete(UUID id) {
		if (!appointmentRepository.existsById(id)) {
			throw new NotFoundException("Appointment not found: " + id);
		}
		appointmentRepository.deleteById(id);
	}

	/**
	 * Automatic counterpart to {@link #purgeHistory} — called by AppointmentRetentionScheduler,
	 * once per tenant, with that tenant's own retention cutoff already computed. A separate
	 * {@code @Transactional} method (not the scheduler calling the repository directly) for the
	 * same reason {@link dev.capibyte.bookingsaas.booking.PendingDepositExpirationScheduler}
	 * delegates to {@link #expireForNonPayment} instead of touching the repository itself: a
	 * derived {@code deleteBy...} query needs an active transaction to run, which a plain
	 * {@code @Component} scheduler method doesn't have on its own.
	 */
	@Transactional
	public long purgeHistoryOlderThan(Instant cutoff) {
		return appointmentRepository.deleteByStartTimeBefore(cutoff);
	}

	/**
	 * Distinct from transitionStatus(CANCELLED): an abandoned checkout is nobody actively telling
	 * anyone they're backing out, so it skips the "first cancellation is free" grace entirely and
	 * always costs the harsher no-show-equivalent penalty — see ClientRatingService.
	 * PendingDepositExpirationScheduler calls this instead of transitionStatus for exactly that
	 * reason.
	 */
	@Transactional
	public Appointment expireForNonPayment(UUID id) {
		Appointment appointment = findById(id);
		validateTransition(appointment.getStatus(), AppointmentStatus.CANCELLED);
		appointment.setStatus(AppointmentStatus.CANCELLED);

		Client client = loadClient(appointment.getClientId());
		Professional professional = professionalService.findById(appointment.getProfessionalId());
		ServiceOffering service = serviceOfferingService.findById(appointment.getServiceId());
		publishNotification(appointment, client, professional, service, AppointmentStatus.CANCELLED);

		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		LocalDate freedDate = appointment.getStartTime().atZone(zone).toLocalDate();
		waitlistService.notifyNextForFreedSlot(appointment.getProfessionalId(), appointment.getServiceId(), freedDate);
		clientRatingService.recordAutoExpired(client);

		return appointment;
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
		// isWhatsappEnabled() is a saved toggle that can outlive a downgrade — a tenant who turned
		// it on while on a WhatsApp-capable plan and then dropped to PERSONAL keeps the toggle at
		// true in the DB (updateWhatsAppEnabled only blocks turning it ON, not off), so the plan's
		// current entitlement must be checked here too, not just once at toggle time.
		boolean whatsappEnabled = tenant.isWhatsappEnabled() && tenant.getPlanTier().isWhatsappEnabled();
		eventPublisher.publishEvent(new AppointmentNotificationEvent(client.getEmail(), client.getName(),
				professional.getDisplayName(), service.getName(), appointment.getStartTime(), zone, status,
				whatsappEnabled, client.getPhone()));
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
