package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.common.ConflictException;

public class InsufficientStockException extends ConflictException {

	public InsufficientStockException() {
		super("Not enough stock available for this product");
	}
}
