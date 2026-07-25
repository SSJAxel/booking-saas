package dev.capibyte.bookingsaas.booking;

import java.time.LocalTime;

public record TimeSlot(LocalTime start, LocalTime end) {
}
