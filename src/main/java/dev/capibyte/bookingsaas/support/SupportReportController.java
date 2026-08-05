package dev.capibyte.bookingsaas.support;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.AppUser;
import dev.capibyte.bookingsaas.identity.AppUserService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Lets any logged-in tenant user report a bug straight to the founder — see SupportReportService. */
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
}
