package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TimezoneUpdateRequest(@NotBlank String timezone) {
}
