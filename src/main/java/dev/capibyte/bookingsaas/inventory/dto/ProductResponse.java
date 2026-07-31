package dev.capibyte.bookingsaas.inventory.dto;

import dev.capibyte.bookingsaas.inventory.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String name, BigDecimal price, int stock, boolean active) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStock(),
				product.isActive());
	}
}
