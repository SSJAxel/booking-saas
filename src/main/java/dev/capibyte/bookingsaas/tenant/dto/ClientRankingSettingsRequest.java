package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ClientRankingSettingsRequest(
		@Min(0) int topClientsThreshold,
		@Min(1) @Max(15) int topClientsCount) {
}
