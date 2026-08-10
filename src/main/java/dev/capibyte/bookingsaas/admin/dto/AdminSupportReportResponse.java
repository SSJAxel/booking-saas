package dev.capibyte.bookingsaas.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** No image URL here on purpose — the frontend builds
 * {@code /api/admin/support-reports/{id}/image} from {@code id} and fetches it as an
 * authenticated blob, since an {@code <img src>} can't carry a Bearer token.
 * {@code hasImage} tells the frontend whether that endpoint has anything to fetch — a
 * PLAN_UPGRADE report never has a screenshot. */
public record AdminSupportReportResponse(
		UUID id,
		UUID tenantId,
		String tenantName,
		String tenantSlug,
		String submitterEmail,
		String type,
		String message,
		boolean hasImage,
		boolean resolved,
		Instant createdAt) {
}
