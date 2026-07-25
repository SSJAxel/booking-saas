package dev.capibyte.bookingsaas.waitlist;

import dev.capibyte.bookingsaas.staff.Professional;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitlistService {

	private final WaitlistRepository waitlistRepository;
	private final ProfessionalService professionalService;
	private final ServiceOfferingService serviceOfferingService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public WaitlistEntry join(UUID professionalId, UUID serviceId, LocalDate date, String clientName,
			String clientEmail, String clientPhone) {
		professionalService.findById(professionalId); // 404s if missing/cross-tenant
		serviceOfferingService.findById(serviceId);

		WaitlistEntry entry = new WaitlistEntry();
		entry.setProfessionalId(professionalId);
		entry.setServiceId(serviceId);
		entry.setDate(date);
		entry.setClientName(clientName);
		entry.setClientEmail(clientEmail);
		entry.setClientPhone(clientPhone);
		return waitlistRepository.save(entry);
	}

	@Transactional(readOnly = true)
	public List<WaitlistEntry> search(UUID professionalId, LocalDate date, WaitlistStatus status) {
		return waitlistRepository.findAll().stream()
				.filter(e -> professionalId == null || e.getProfessionalId().equals(professionalId))
				.filter(e -> date == null || e.getDate().equals(date))
				.filter(e -> status == null || e.getStatus() == status)
				.toList();
	}

	/**
	 * Called from AppointmentService when a cancellation frees up a slot. Notifies only the
	 * single oldest WAITING entry for that professional/service/date (real FIFO semantics, not a
	 * mailing list) — the client can then re-check availability and book, first come first served.
	 */
	@Transactional
	public void notifyNextForFreedSlot(UUID professionalId, UUID serviceId, LocalDate date) {
		waitlistRepository
				.findFirstByProfessionalIdAndServiceIdAndDateAndStatusOrderByCreatedAtAsc(professionalId, serviceId,
						date, WaitlistStatus.WAITING)
				.ifPresent(entry -> {
					entry.setStatus(WaitlistStatus.NOTIFIED);
					Professional professional = professionalService.findById(professionalId);
					ServiceOffering service = serviceOfferingService.findById(serviceId);
					eventPublisher.publishEvent(new WaitlistNotificationEvent(entry.getClientEmail(),
							entry.getClientName(), professional.getDisplayName(), service.getName(), date));
				});
	}
}
