package dev.capibyte.bookingsaas.notification;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrphanedDepositPaymentListenerTest {

	private final MailService mailService = mock(MailService.class);
	private final OrphanedDepositPaymentListener listener = new OrphanedDepositPaymentListener(mailService);

	@Test
	void emailsTheOwnerWithClientAndAmountDetails() {
		listener.onOrphanedDepositPayment(new OrphanedDepositPaymentEvent("owner@example.com", "Lusi Tattoo",
				"Jane Doe", "jane@example.com", "Small Tattoo", new BigDecimal("5000.00")));

		verify(mailService).send(eq("owner@example.com"), contains("cancelado"),
				argThat(body -> body.contains("Jane Doe") && body.contains("jane@example.com")
						&& body.contains("5000.00") && body.contains("Small Tattoo")));
	}
}
