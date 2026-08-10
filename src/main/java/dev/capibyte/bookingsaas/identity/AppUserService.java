package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

	private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public AppUser createOwner(String email, String rawPassword) {
		AppUser user = new AppUser();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setRole(Role.OWNER);
		issueVerificationToken(user);
		return appUserRepository.save(user);
	}

	@Transactional(readOnly = true)
	public Optional<AppUser> findActiveByEmail(String email) {
		return appUserRepository.findByEmail(email).filter(AppUser::isActive);
	}

	/**
	 * Called with {@link dev.capibyte.bookingsaas.common.TenantContext} already set to the tenant
	 * resolved from the verification link's {@code tenantSlug} — the token column is globally
	 * unique, but the lookup still goes through the normal @TenantId-filtered repository like
	 * every other tenant-scoped query, so a token can only ever verify a user in its own tenant.
	 */
	@Transactional
	public AppUser verifyEmail(String token) {
		AppUser user = appUserRepository.findByVerificationToken(token)
				.orElseThrow(() -> new BadRequestException("Invalid or already-used verification link"));
		if (user.getVerificationTokenExpiresAt() == null || user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
			throw new BadRequestException("This verification link expired — request a new one");
		}
		user.setEmailVerified(true);
		user.setVerificationToken(null);
		user.setVerificationTokenExpiresAt(null);
		return user;
	}

	/**
	 * Empty result covers both "no such user" and "already verified" — the caller (see
	 * AuthService.resendVerification) responds identically either way, so a prospective attacker
	 * can't use this to probe which emails are registered.
	 */
	@Transactional
	public Optional<AppUser> regenerateVerificationToken(String email) {
		return appUserRepository.findByEmail(email)
				.filter(user -> !user.isEmailVerified())
				.map(user -> {
					issueVerificationToken(user);
					return user;
				});
	}

	/** Used by PlatformAdminService#approveTenant to find who to email — see that method's Javadoc
	 * for why this must be a call through this bean's own Spring proxy (a fresh session, opened
	 * after the caller has already set TenantContext), not a same-class helper. */
	@Transactional(readOnly = true)
	public Optional<AppUser> findOwner() {
		return appUserRepository.findFirstByRole(Role.OWNER);
	}

	@Transactional(readOnly = true)
	public AppUser findById(UUID id) {
		return appUserRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
	}

	@Transactional
	public AppUser updateProfile(UUID id, String displayName, String avatarUrl) {
		AppUser user = findById(id);
		user.setDisplayName(displayName);
		user.setAvatarUrl(avatarUrl);
		return user;
	}

	private void issueVerificationToken(AppUser user) {
		user.setVerificationToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
		user.setVerificationTokenExpiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL));
	}
}
