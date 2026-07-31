package dev.capibyte.bookingsaas.inventory.dto;

import dev.capibyte.bookingsaas.inventory.Sale;
import java.math.BigDecimal;
import java.util.UUID;

public record SaleResponse(UUID id, UUID productId, UUID appointmentId, int quantity, BigDecimal amount) {

	public static SaleResponse from(Sale sale) {
		return new SaleResponse(sale.getId(), sale.getProductId(), sale.getAppointmentId(), sale.getQuantity(),
				sale.getAmount());
	}
}
