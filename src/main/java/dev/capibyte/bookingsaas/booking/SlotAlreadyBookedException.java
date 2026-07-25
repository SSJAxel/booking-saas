package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.ConflictException;

public class SlotAlreadyBookedException extends ConflictException {

	public SlotAlreadyBookedException() {
		super("This time slot is no longer available for the selected professional");
	}
}
