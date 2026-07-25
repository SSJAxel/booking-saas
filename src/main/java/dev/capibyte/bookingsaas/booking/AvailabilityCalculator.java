package dev.capibyte.bookingsaas.booking;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure slot math: open windows (from weekly hours) minus blocked ranges (time off, and — once
 * appointments exist — existing bookings), sliced into service-duration increments. Deliberately
 * takes "blocked ranges" as a plain list rather than knowing about time-off/appointments itself,
 * so callers can merge in whatever sources of unavailability apply.
 */
@Component
public class AvailabilityCalculator {

	public List<TimeSlot> freeSlots(List<TimeSlot> openWindows, List<TimeSlot> blockedRanges, int durationMinutes) {
		List<TimeSlot> free = new ArrayList<>();
		for (TimeSlot window : openWindows) {
			for (TimeSlot piece : subtract(window, blockedRanges)) {
				free.addAll(slice(piece, durationMinutes));
			}
		}
		return free;
	}

	private List<TimeSlot> subtract(TimeSlot window, List<TimeSlot> blocked) {
		List<TimeSlot> remaining = new ArrayList<>(List.of(window));
		for (TimeSlot blockedRange : blocked) {
			List<TimeSlot> next = new ArrayList<>();
			for (TimeSlot piece : remaining) {
				if (!blockedRange.start().isBefore(piece.end()) || !blockedRange.end().isAfter(piece.start())) {
					next.add(piece); // no overlap
					continue;
				}
				if (blockedRange.start().isAfter(piece.start())) {
					next.add(new TimeSlot(piece.start(), blockedRange.start()));
				}
				if (blockedRange.end().isBefore(piece.end())) {
					next.add(new TimeSlot(blockedRange.end(), piece.end()));
				}
			}
			remaining = next;
		}
		return remaining;
	}

	private List<TimeSlot> slice(TimeSlot window, int durationMinutes) {
		List<TimeSlot> slots = new ArrayList<>();
		LocalTime cursor = window.start();
		while (!cursor.plusMinutes(durationMinutes).isAfter(window.end())) {
			LocalTime end = cursor.plusMinutes(durationMinutes);
			slots.add(new TimeSlot(cursor, end));
			cursor = end;
		}
		return slots;
	}
}
