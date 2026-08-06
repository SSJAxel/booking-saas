package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code depositAmount} is null when the service doesn't require one — the client needs this
 * up front (before booking, and again on the confirmation screen) to know how much to transfer. */
public record PublicServiceResponse(UUID id, String name, String description, int durationMinutes, BigDecimal price,
		BigDecimal depositAmount) {

	public static PublicServiceResponse from(ServiceOffering service) {
		return new PublicServiceResponse(service.getId(), service.getName(), service.getDescription(),
				service.getDurationMinutes(), service.getPrice(), service.getDepositAmount());
	}
}
