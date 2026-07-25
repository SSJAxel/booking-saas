package dev.capibyte.bookingsaas.common;

import java.time.Instant;
import java.util.List;

public record ApiError(String error, String message, Instant timestamp, List<FieldViolation> fieldErrors) {

	public record FieldViolation(String field, String message) {
	}

	public static ApiError of(String error, String message) {
		return new ApiError(error, message, Instant.now(), List.of());
	}

	public static ApiError of(String error, String message, List<FieldViolation> fieldErrors) {
		return new ApiError(error, message, Instant.now(), fieldErrors);
	}
}
