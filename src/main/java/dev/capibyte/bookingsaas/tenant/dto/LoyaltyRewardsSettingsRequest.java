package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LoyaltyRewardsSettingsRequest(boolean enabled, @Min(5) @Max(200) int pointsCap) {
}
