package dev.capibyte.bookingsaas.report.dto;

import java.util.UUID;

public record ProfessionalCount(UUID professionalId, String displayName, long count) {
}
