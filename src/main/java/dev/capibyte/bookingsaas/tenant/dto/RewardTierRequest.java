package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RewardTierRequest(@Min(1) int pointsRequired, @NotBlank @Size(max = 255) String description) {
}
