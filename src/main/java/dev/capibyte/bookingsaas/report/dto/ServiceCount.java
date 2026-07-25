package dev.capibyte.bookingsaas.report.dto;

import java.util.UUID;

public record ServiceCount(UUID serviceId, String name, long count) {
}
