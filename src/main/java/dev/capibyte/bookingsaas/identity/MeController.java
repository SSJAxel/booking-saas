package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.dto.MeResponse;
import dev.capibyte.bookingsaas.identity.dto.MeUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

	private final AppUserService appUserService;

	@GetMapping
	public MeResponse me(Authentication authentication) {
		UUID userId = (UUID) authentication.getPrincipal();
		return toResponse(appUserService.findById(userId), roleOf(authentication));
	}

	/** Personal profile only (display name, avatar) — password changes aren't self-service yet. */
	@PatchMapping
	public MeResponse update(Authentication authentication, @Valid @RequestBody MeUpdateRequest request) {
		UUID userId = (UUID) authentication.getPrincipal();
		AppUser user = appUserService.updateProfile(userId, request.displayName(), request.avatarUrl());
		return toResponse(user, roleOf(authentication));
	}

	private String roleOf(Authentication authentication) {
		return authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
	}

	private MeResponse toResponse(AppUser user, String role) {
		return new MeResponse(user.getId(), TenantContext.getTenantId(), role, user.getEmail(), user.getDisplayName(),
				user.getAvatarUrl(), user.isPlatformAdmin());
	}
}
