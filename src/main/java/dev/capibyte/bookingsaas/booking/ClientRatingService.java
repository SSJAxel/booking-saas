package dev.capibyte.bookingsaas.booking;

import org.springframework.stereotype.Service;

/**
 * Moves a {@link Client}'s loyalty {@code rating} in response to how an appointment resolves.
 * Takes the already-loaded, persistence-context-managed {@link Client} entity rather than an id —
 * every caller already has it loaded, and mutating it directly lets Hibernate's normal dirty
 * checking persist the change at the caller's transaction commit, with no extra query needed here.
 *
 * <p>Rules (agreed with the tenant while designing this): a completed visit is worth +1. A
 * client's first-ever cancellation is free (people's plans change); the second and every one
 * after costs 2 points. A no-show — booked, never showed, never said a word — costs 5, since it
 * burns the slot with zero warning. An appointment auto-cancelled by {@code
 * PendingDepositExpirationScheduler} (an abandoned checkout, not a client actively telling anyone
 * they're backing out) costs the same 5 automatically, every time — it deliberately does NOT
 * count toward {@link Client#getCancelledCount()}, so it can never eat into or trigger the
 * cancellation grace. Rating never goes below 0; there's no upper cap, so a genuinely loyal client
 * can keep climbing indefinitely past the tenant's "top clients" threshold.
 */
@Service
public class ClientRatingService {

	private static final int COMPLETED_POINTS = 1;
	private static final int REPEAT_CANCELLATION_PENALTY = 2;
	private static final int NO_SHOW_PENALTY = 5;
	private static final int AUTO_EXPIRED_PENALTY = 5;

	public void recordCompleted(Client client) {
		client.setRating(client.getRating() + COMPLETED_POINTS);
	}

	public void recordCancellation(Client client) {
		if (client.getCancelledCount() > 0) {
			applyPenalty(client, REPEAT_CANCELLATION_PENALTY);
		}
		client.setCancelledCount(client.getCancelledCount() + 1);
	}

	public void recordNoShow(Client client) {
		applyPenalty(client, NO_SHOW_PENALTY);
	}

	public void recordAutoExpired(Client client) {
		applyPenalty(client, AUTO_EXPIRED_PENALTY);
	}

	private void applyPenalty(Client client, int points) {
		client.setRating(Math.max(0, client.getRating() - points));
	}
}
