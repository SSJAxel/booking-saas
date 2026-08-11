package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record HistoryRetentionUpdateRequest(@Min(1) @Max(12) int historyRetentionMonths) {
}
