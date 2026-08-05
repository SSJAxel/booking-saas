package dev.capibyte.bookingsaas.report.dto;

import java.time.LocalDate;

public record DailyCountResponse(LocalDate date, long count) {
}
