package dev.capibyte.bookingsaas.support;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.AppUser;
import dev.capibyte.bookingsaas.identity.AppUserService;
import dev.capibyte.bookingsaas.support.dto.PlanUpgradeRequestBody;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Lets any logged-in tenant user report a bug or ask to upgrade their plan straight to the
 * founder — see SupportReportService. */
@RestController
@RequestMapping("/api/support-reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class SupportReportController {

	private final SupportReportService supportReportService;
	private final AppUserService appUserService;
	private final TenantService tenantService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public void report(Authentication authentication, @RequestParam("message") String message,
			@RequestParam("image") MultipartFile image) {
		UUID userId = (UUID) authentication.getPrincipal();
		AppUser user = appUserService.findById(userId);
		String tenantName = tenantService.findById(TenantContext.getTenantId()).getName();
		supportReportService.create(message, image, userId, user.getEmail(), tenantName);
	}

	/** "Mejorar plan" button on Mi Plan — alerts the founder instead of an automated checkout, for
	 * plans without a self-service price (MAX) or a custom deal. See
	 * SupportReportService#createPlanUpgradeRequest. */
	@PostMapping("/plan-upgrade")
	@ResponseStatus(HttpStatus.CREATED)
	public void requestPlanUpgrade(Authentication authentication, @RequestBody(required = false) PlanUpgradeRequestBody body) {
		UUID userId = (UUID) authentication.getPrincipal();
		AppUser user = appUserService.findById(userId);
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		String note = body == null ? null : body.note();
		supportReportService.createPlanUpgradeRequest(note, userId, user.getEmail(), tenant.getName(),
				tenant.getSlug());
	}
}
