package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.identity.dto.AuthResponse;
import dev.capibyte.bookingsaas.identity.dto.LoginRequest;
import dev.capibyte.bookingsaas.identity.dto.RegisterRequest;
import dev.capibyte.bookingsaas.identity.dto.RegisterResponse;
import dev.capibyte.bookingsaas.identity.dto.ResendVerificationRequest;
import dev.capibyte.bookingsaas.identity.dto.VerifyEmailRequest;
import dev.capibyte.bookingsaas.notification.MailService;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT @Transactional at this level: a single Hibernate session resolves its tenant
 * once, when it's opened, so tenantService.create(...) and appUserService.createOwner(...) each
 * need their own transaction/session boundary with {@link TenantContext} set in between — see
 * the Javadoc on TenantService and AppUserService for why.
 */
@Service
public class AuthService {

	private final TenantService tenantService;
	private final AppUserService appUserService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final MailService mailService;
	private final String verificationBaseUrl;

	public AuthService(TenantService tenantService, AppUserService appUserService, PasswordEncoder passwordEncoder,
			JwtService jwtService, MailService mailService,
			@Value("${app.mail.verification-base-url}") String verificationBaseUrl) {
		this.tenantService = tenantService;
		this.appUserService = appUserService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.mailService = mailService;
		this.verificationBaseUrl = verificationBaseUrl;
	}

	/**
	 * No token in the response on purpose — anyone could otherwise spin up an unlimited number of
	 * working accounts with a fake email, which is exactly the abuse this gate exists to close.
	 * The account is real and stored, but {@link #login} refuses it until {@link #verifyEmail}
	 * runs, which only a click on the mailed link can trigger.
	 */
	public RegisterResponse register(RegisterRequest request) {
		Tenant tenant = tenantService.create(request.tenantName(), request.tenantSlug());

		TenantContext.setTenantId(tenant.getId());
		try {
			AppUser owner = appUserService.createOwner(request.ownerEmail(), request.ownerPassword());
			sendVerificationEmail(tenant, owner);
			return new RegisterResponse(tenant.getId(), tenant.getSlug(), owner.getEmail());
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
			if (!user.isEmailVerified()) {
				throw new EmailNotVerifiedException();
			}

			return authResponse(tenant, user);
		} finally {
			TenantContext.clear();
		}
	}

	/** Auto-logs the owner in on success — one less step after they've already proven the email is real. */
	public AuthResponse verifyEmail(VerifyEmailRequest request) {
		Tenant tenant = tenantService.findBySlug(request.tenantSlug())
				.orElseThrow(InvalidCredentialsException::new);

		TenantContext.setTenantId(tenant.getId());
		try {
			AppUser user = appUserService.verifyEmail(request.token());
			return authResponse(tenant, user);
		} finally {
			TenantContext.clear();
		}
	}

	/** Always succeeds from the caller's point of view, whether or not the email is registered — see AppUserService.regenerateVerificationToken. */
	public void resendVerification(ResendVerificationRequest request) {
		tenantService.findBySlug(request.tenantSlug()).ifPresent(tenant -> {
			TenantContext.setTenantId(tenant.getId());
			try {
				appUserService.regenerateVerificationToken(request.email())
						.ifPresent(user -> sendVerificationEmail(tenant, user));
			} finally {
				TenantContext.clear();
			}
		});
	}

	private void sendVerificationEmail(Tenant tenant, AppUser owner) {
		String link = verificationBaseUrl + "?tenant=" + tenant.getSlug() + "&token=" + owner.getVerificationToken();
		mailService.send(owner.getEmail(), "Confirm your email",
				"Hi,\n\nConfirm your email to activate " + tenant.getName() + " on booking-saas:\n" + link
						+ "\n\nThis link expires in 24 hours.");
	}

	private AuthResponse authResponse(Tenant tenant, AppUser user) {
		String token = jwtService.generateToken(user.getId(), tenant.getId(), user.getEmail(), user.getRole(),
				user.isPlatformAdmin());
		return new AuthResponse(token, tenant.getId(), tenant.getSlug(), user.getId(), user.getEmail(),
				user.getRole().name(), user.isPlatformAdmin());
	}
}
