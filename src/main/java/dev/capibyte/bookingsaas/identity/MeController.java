package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.dto.MeResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

	@GetMapping
	public MeResponse me(Authentication authentication) {
		UUID userId = (UUID) authentication.getPrincipal();
		String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
		return new MeResponse(userId, TenantContext.getTenantId(), role);
	}
}
