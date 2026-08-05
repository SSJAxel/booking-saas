package dev.capibyte.bookingsaas.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesResponse(LocalDate date, BigDecimal totalAmount) {
}
