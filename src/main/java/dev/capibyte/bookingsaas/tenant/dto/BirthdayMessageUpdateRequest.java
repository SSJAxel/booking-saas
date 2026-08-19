package dev.capibyte.bookingsaas.tenant.dto;

import jakarta.validation.constraints.Size;

/** Null/blank turns the automated birthday email off — see Tenant.birthdayMessageTemplate. */
public record BirthdayMessageUpdateRequest(@Size(max = 1000) String message) {
}
