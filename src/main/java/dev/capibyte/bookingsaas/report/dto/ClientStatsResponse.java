package dev.capibyte.bookingsaas.report.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-client rollup across every appointment they've ever had with this tenant — {@code
 * completedCount} is the "actually showed up" signal (repeat/frequent clients), {@code
 * cancelledCount} is the "books and bails" signal, kept separate rather than folded into a single
 * score so the owner can judge each independently instead of trusting one opaque number.
 */
public record ClientStatsResponse(
		UUID clientId,
		String clientName,
		String clientEmail,
		long totalAppointments,
		long completedCount,
		long cancelledCount,
		long noShowCount,
		int rating,
		boolean pinned,
		String notes,
		int loyaltyPoints,
		LocalDate birthDate,
		String servicePreferences,
		String allergies) {
}
