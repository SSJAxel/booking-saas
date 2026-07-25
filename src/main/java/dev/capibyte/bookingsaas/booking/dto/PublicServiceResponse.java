package dev.capibyte.bookingsaas.booking.dto;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

public record PublicServiceResponse(UUID id, String name, String description, int durationMinutes, BigDecimal price) {

	public static PublicServiceResponse from(ServiceOffering service) {
		return new PublicServiceResponse(service.getId(), service.getName(), service.getDescription(),
				service.getDurationMinutes(), service.getPrice());
	}
}
