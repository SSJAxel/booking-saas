package dev.capibyte.bookingsaas.catalog.dto;

import dev.capibyte.bookingsaas.catalog.ServiceCombo;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceComboResponse(UUID id, UUID serviceAId, UUID serviceBId, BigDecimal comboPrice,
		BigDecimal comboDepositAmount, boolean active) {

	public static ServiceComboResponse from(ServiceCombo combo) {
		return new ServiceComboResponse(combo.getId(), combo.getServiceAId(), combo.getServiceBId(),
				combo.getComboPrice(), combo.getComboDepositAmount(), combo.isActive());
	}
}
