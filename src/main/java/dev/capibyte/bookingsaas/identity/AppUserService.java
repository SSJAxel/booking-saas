package dev.capibyte.bookingsaas.identity;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Its own bean for the same reason as {@link dev.capibyte.bookingsaas.tenant.TenantService}:
 * each @Transactional method here must open a fresh Hibernate session so the tenant that
 * {@link dev.capibyte.bookingsaas.common.TenantContext} was just set to (by the caller, right
 * before invoking this bean) is the one Hibernate resolves for @TenantId stamping/filtering.
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public AppUser createOwner(String email, String rawPassword) {
		AppUser user = new AppUser();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setRole(Role.OWNER);
		return appUserRepository.save(user);
	}

	@Transactional(readOnly = true)
	public Optional<AppUser> findActiveByEmail(String email) {
		return appUserRepository.findByEmail(email).filter(AppUser::isActive);
	}
}
