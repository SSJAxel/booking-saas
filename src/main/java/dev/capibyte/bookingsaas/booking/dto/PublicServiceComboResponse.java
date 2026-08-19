package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.catalog.ServiceCombo;
import java.math.BigDecimal;

/** Preview only, shown before the client confirms — {@code AppointmentService#bookGroup} is the
 * one that actually re-derives and applies this server-side at booking time, never trusts a value
 * the client might have cached from this earlier call. */
public record PublicServiceComboResponse(BigDecimal comboPrice, BigDecimal comboDepositAmount) {

	public static PublicServiceComboResponse from(ServiceCombo combo) {
		return new PublicServiceComboResponse(combo.getComboPrice(), combo.getComboDepositAmount());
	}
}
