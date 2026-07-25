package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.dto.AuthResponse;
import dev.capibyte.bookingsaas.identity.dto.LoginRequest;
import dev.capibyte.bookingsaas.identity.dto.RegisterRequest;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT @Transactional at this level: a single Hibernate session resolves its tenant
 * once, when it's opened, so tenantService.create(...) and appUserService.createOwner(...) each
 * need their own transaction/session boundary with {@link TenantContext} set in between — see
 * the Javadoc on TenantService and AppUserService for why.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final TenantService tenantService;
	private final AppUserService appUserService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthResponse register(RegisterRequest request) {
		Tenant tenant = tenantService.create(request.tenantName(), request.tenantSlug());

		TenantContext.setTenantId(tenant.getId());
		try {
			AppUser owner = appUserService.createOwner(request.ownerEmail(), request.ownerPassword());
			String token = jwtService.generateToken(owner.getId(), tenant.getId(), owner.getEmail(), owner.getRole());
			return new AuthResponse(token, tenant.getId(), tenant.getSlug(), owner.getId(), owner.getEmail(),
					owner.getRole().name());
		} catch (RuntimeException ex) {
			// Best-effort compensation: the two-step create isn't atomic across the tenant/owner
			// session boundary above, so a failed owner insert would otherwise orphan the tenant.
			tenantService.delete(tenant.getId());
			throw ex;
		} finally {
			TenantContext.clear();
		}
	}

	public AuthResponse login(LoginRequest request) {
		Tenant tenant = tenantService.findBySlug(request.tenantSlug())
				.orElseThrow(InvalidCredentialsException::new);

		TenantContext.setTenantId(tenant.getId());
		try {
			AppUser user = appUserService.findActiveByEmail(request.email())
					.orElseThrow(InvalidCredentialsException::new);

			if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
				throw new InvalidCredentialsException();
			}

			String token = jwtService.generateToken(user.getId(), tenant.getId(), user.getEmail(), user.getRole());
			return new AuthResponse(token, tenant.getId(), tenant.getSlug(), user.getId(), user.getEmail(),
					user.getRole().name());
		} finally {
			TenantContext.clear();
		}
	}
}
