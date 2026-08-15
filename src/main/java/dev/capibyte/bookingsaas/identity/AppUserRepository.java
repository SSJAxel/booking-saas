package dev.capibyte.bookingsaas.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No tenant_id parameter anywhere here on purpose — {@code @TenantId} on {@link AppUser}
 * makes Hibernate append the current-tenant filter to every query this repository runs.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByEmail(String email);

	Optional<AppUser> findFirstByRole(Role role);

	Optional<AppUser> findByVerificationToken(String verificationToken);

	Optional<AppUser> findByResetToken(String resetToken);
}
