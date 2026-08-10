package dev.capibyte.bookingsaas.support.dto;

/** {@code note} is optional — a tenant can just hit "Mejorar plan" with no extra context. */
public record PlanUpgradeRequestBody(String note) {
}
