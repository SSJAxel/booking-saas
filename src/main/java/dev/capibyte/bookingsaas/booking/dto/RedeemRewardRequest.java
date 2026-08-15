package dev.capibyte.bookingsaas.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RedeemRewardRequest(@NotNull UUID rewardTierId) {
}
