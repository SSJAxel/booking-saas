package dev.capibyte.bookingsaas.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** No image URL here on purpose — the frontend builds
 * {@code /api/admin/support-reports/{id}/image} from {@code id} and fetches it as an
 * authenticated blob, since an {@code <img src>} can't carry a Bearer token. */
public record AdminSupportReportResponse(
		UUID id,
		UUID tenantId,
		String tenantName,
		String tenantSlug,
		String submitterEmail,
		String message,
		boolean resolved,
		Instant createdAt) {
}
