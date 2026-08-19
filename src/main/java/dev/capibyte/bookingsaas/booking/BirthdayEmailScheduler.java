package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.notification.MailService;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MAX-only (see PlanTier#isBirthdayAutoEmailEnabled) — sends the tenant's own custom
 * {@code birthdayMessageTemplate} to any client whose birthday is "today" in that tenant's own
 * timezone, {@code {nombre}} substituted with the client's name. A tenant with no message
 * configured is the "off" state (see Tenant.birthdayMessageTemplate's Javadoc), skipped without
 * even querying its clients. Same manual loop-with-TenantContext pattern as
 * AppointmentRetentionScheduler, for the same reason: Client is @TenantId-scoped and this runs
 * outside any request/JWT.
 *
 * <p>Deliberately does NOT touch anything about a discount — see PlanTier's Javadoc for why this
 * whole feature stops at "remind/congratulate", never "automatically apply a discount".
 */
@Component
public class BirthdayEmailScheduler {

	private static final Logger log = LoggerFactory.getLogger(BirthdayEmailScheduler.class);

	private final TenantRepository tenantRepository;
	private final ClientRepository clientRepository;
	private final MailService mailService;

	public BirthdayEmailScheduler(TenantRepository tenantRepository, ClientRepository clientRepository,
			MailService mailService) {
		this.tenantRepository = tenantRepository;
		this.clientRepository = clientRepository;
		this.mailService = mailService;
	}

	@Scheduled(fixedDelayString = "${app.booking.birthday-check-interval-ms:86400000}")
	public void sendBirthdayEmails() {
		for (Tenant tenant : tenantRepository.findAll()) {
			if (!tenant.getPlanTier().isBirthdayAutoEmailEnabled()) {
				continue;
			}
			String template = tenant.getBirthdayMessageTemplate();
			if (template == null || template.isBlank()) {
				continue;
			}
			TenantContext.setTenantId(tenant.getId());
			try {
				LocalDate today = LocalDate.now(ZoneId.of(tenant.getTimezone()));
				int year = today.getYear();
				for (Client client : clientRepository.findByBirthMonthAndDay(today.getMonthValue(), today.getDayOfMonth())) {
					if (client.getLastBirthdayEmailYear() != null && client.getLastBirthdayEmailYear() == year) {
						continue; // already sent this year — see Client.lastBirthdayEmailYear's Javadoc
					}
					// Courtesy send, same category MailService's own Javadoc gives as its example (an
					// appointment confirmation) — ignore the return value on purpose. Gating the
					// dedupe marker on success would mean a transient SMTP hiccup today silently
					// costs this client their birthday email for the entire year, since the "who's
					// today" lookup only ever matches on the actual calendar day.
					String body = template.replace("{nombre}", client.getName());
					mailService.send(client.getEmail(), "¡Feliz cumpleaños de parte de " + tenant.getName() + "!", body);
					client.setLastBirthdayEmailYear(year);
					clientRepository.save(client);
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
