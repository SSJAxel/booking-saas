package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.identity.dto.AuthResponse;
import dev.capibyte.bookingsaas.identity.dto.LoginRequest;
import dev.capibyte.bookingsaas.identity.dto.RegisterRequest;
import dev.capibyte.bookingsaas.identity.dto.RegisterResponse;
import dev.capibyte.bookingsaas.identity.dto.ResendVerificationRequest;
import dev.capibyte.bookingsaas.identity.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/verify-email")
	public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return authService.verifyEmail(request);
	}

	/**
	 * Always 204, whether or not the email/tenant combination exists — see
	 * AuthService.resendVerification for why the response can't reveal that.
	 */
	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		authService.resendVerification(request);
	}
}
