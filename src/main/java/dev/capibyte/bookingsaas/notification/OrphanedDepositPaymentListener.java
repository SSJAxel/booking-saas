package dev.capibyte.bookingsaas.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owner-facing (Spanish, matching PlatformAdminService's other tenant-owner emails — not
 * client-facing like AppointmentNotificationListener, which is English) alert for a deposit that
 * got paid too late — the slot was already given back. No refund flow exists anywhere in this
 * codebase (see README "Design notes" → Payments/deposits), so this can't resolve the mismatch
 * automatically; it only makes sure the owner finds out instead of a paid, cancelled appointment
 * sitting silently with nobody aware of it.
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
						+ "libre — si es así, podés coordinar con el cliente para confirmarlo a mano. Si no, vas a "
						+ "tener que resolver un reembolso desde Mercado Pago; el sistema todavía no lo hace solo.");
	}
}
