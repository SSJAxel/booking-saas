package dev.capibyte.bookingsaas.catalog.dto;

import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingResponse(UUID id, String name, String description, String category,
		int durationMinutes, BigDecimal price, BigDecimal depositAmount, boolean active) {

	public static ServiceOfferingResponse from(ServiceOffering service) {
		return new ServiceOfferingResponse(service.getId(), service.getName(), service.getDescription(),
				service.getCategory(), service.getDurationMinutes(), service.getPrice(), service.getDepositAmount(),
				service.isActive());
	}
}
