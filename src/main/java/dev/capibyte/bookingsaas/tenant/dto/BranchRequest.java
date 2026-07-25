package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(@NotBlank String name, String address, String phone) {
}
