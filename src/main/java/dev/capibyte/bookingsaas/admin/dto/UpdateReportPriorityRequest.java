package dev.capibyte.bookingsaas.admin.dto;

import dev.capibyte.bookingsaas.support.SupportReportPriority;
import jakarta.validation.constraints.NotNull;

public record UpdateReportPriorityRequest(@NotNull SupportReportPriority priority) {
}
