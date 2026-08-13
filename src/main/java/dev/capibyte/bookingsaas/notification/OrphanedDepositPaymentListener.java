package dev.capibyte.bookingsaas.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owner-facing (Spanish, matching PlatformAdminService's other tenant-owner emails — not
 * client-facing like AppointmentNotificationListener, which is English) alert for a deposit that
 * got paid too late — the slot was already given back. Deliberately doesn't suggest a refund:
 * this platform's policy is that a paid deposit is never refunded through the system, no matter
 * the reason the appointment didn't happen (see README "Design notes" → Payments/deposits) — this
 * only makes sure the owner finds out instead of a paid, cancelled appointment sitting silently
 * with nobody aware of it, so they can decide by hand whether the slot's still worth honoring.
 */
@Component
@RequiredArgsConstructor
public class OrphanedDepositPaymentListener {

	private final MailService mailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOrphanedDepositPayment(OrphanedDepositPaymentEvent event) {
		mailService.send(event.ownerEmail(), "Seña cobrada en un turno ya cancelado",
				"Hola,\n\n" + event.clientName() + " (" + event.clientEmail() + ") pagó una seña de $"
						+ event.amount() + " para \"" + event.serviceName() + "\", pero ese turno ya se había "
						+ "cancelado automáticamente por falta de pago a tiempo.\n\nFijate si el horario sigue "
						+ "libre — si es así, podés coordinar con el cliente para confirmarlo a mano.");
	}
}
