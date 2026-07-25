package dev.capibyte.bookingsaas.catalog.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignProfessionalRequest(@NotNull UUID professionalId) {
}
