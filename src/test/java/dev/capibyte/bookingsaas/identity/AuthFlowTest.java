package dev.capibyte.bookingsaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthFlowTest extends IntegrationTestBase {

	@Test
	void registerThenLoginThenAccessProtectedEndpoint() {
		RegisteredTenant tenant = registerTenant();

		ResponseEntity<Map> me = restTemplate.exchange("/api/me", HttpMethod.GET,
				new HttpEntity<>(authHeaders(tenant.token())), Map.class);

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody().get("role")).isEqualTo("OWNER");
	}

	@Test
	void protectedEndpointWithoutTokenReturns401() {
		ResponseEntity<Map> me = restTemplate.getForEntity("/api/me", Map.class);

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginWithWrongPasswordReturns401() {
		RegisteredTenant tenant = registerTenant();

		Map<String, Object> loginRequest = Map.of(
				"tenantSlug", tenant.slug(),
				"email", tenant.slug() + "@example.com",
				"password", "not-the-real-password");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void registeringTheSameSlugTwiceReturns409() {
		String slug = "dup-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> request = Map.of(
				"tenantName", "First",
				"tenantSlug", slug,
				"ownerEmail", "first-" + slug + "@example.com",
				"ownerPassword", "supersecret123");
		restTemplate.postForEntity("/api/auth/register", request, Map.class);

		Map<String, Object> duplicate = Map.of(
				"tenantName", "Second",
				"tenantSlug", slug,
				"ownerEmail", "second-" + slug + "@example.com",
				"ownerPassword", "supersecret123");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", duplicate, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void registerDoesNotReturnAUsableTokenAndLoginFailsUntilVerified() {
		String slug = "unverified-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		Map<String, Object> request = Map.of(
				"tenantName", "Unverified", "tenantSlug", slug, "ownerEmail", email, "ownerPassword", "supersecret123");

		ResponseEntity<Map> registerResponse = restTemplate.postForEntity("/api/auth/register", request, Map.class);

		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(registerResponse.getBody()).doesNotContainKey("token");
		assertThat(registerResponse.getBody().get("tenantSlug")).isEqualTo(slug);

		Map<String, Object> loginRequest = Map.of(
				"tenantSlug", slug, "email", email, "password", "supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);

		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void verifyingEmailAllowsLoginAndReturnsAWorkingToken() {
		String slug = "verify-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		Map<String, Object> request = Map.of(
				"tenantName", "Verify Me", "tenantSlug", slug, "ownerEmail", email, "ownerPassword", "supersecret123");
		restTemplate.postForEntity("/api/auth/register", request, Map.class);

		String token = jdbcTemplate.queryForObject(
				"SELECT verification_token FROM app_users WHERE email = ?", String.class, email);

		ResponseEntity<Map> verifyResponse = restTemplate.postForEntity("/api/auth/verify-email",
				Map.of("tenantSlug", slug, "token", token), Map.class);

		assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String sessionToken = (String) verifyResponse.getBody().get("token");
		assertThat(sessionToken).isNotBlank();

		ResponseEntity<Map> me = restTemplate.exchange("/api/me", HttpMethod.GET,
				new HttpEntity<>(authHeaders(sessionToken)), Map.class);
		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login",
				Map.of("tenantSlug", slug, "email", email, "password", "supersecret123"), Map.class);
		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void verifyEmailWithAnInvalidTokenIsRejected() {
		RegisteredTenant tenant = registerTenant();

		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/verify-email",
				Map.of("tenantSlug", tenant.slug(), "token", "not-a-real-token"), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void verifyEmailWithAnExpiredTokenIsRejected() {
		String slug = "expired-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		restTemplate.postForEntity("/api/auth/register", Map.of(
				"tenantName", "Expired", "tenantSlug", slug, "ownerEmail", email, "ownerPassword", "supersecret123"),
				Map.class);

		jdbcTemplate.update("UPDATE app_users SET verification_token_expires_at = ? WHERE email = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), email);
		String token = jdbcTemplate.queryForObject(
				"SELECT verification_token FROM app_users WHERE email = ?", String.class, email);

		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/verify-email",
				Map.of("tenantSlug", slug, "token", token), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void resendVerificationAlwaysReturnsNoContentAndIssuesANewToken() {
		String slug = "resend-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		restTemplate.postForEntity("/api/auth/register", Map.of(
				"tenantName", "Resend", "tenantSlug", slug, "ownerEmail", email, "ownerPassword", "supersecret123"),
				Map.class);
		String firstToken = jdbcTemplate.queryForObject(
				"SELECT verification_token FROM app_users WHERE email = ?", String.class, email);

		ResponseEntity<Void> response = restTemplate.postForEntity("/api/auth/resend-verification",
				Map.of("tenantSlug", slug, "email", email), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String secondToken = jdbcTemplate.queryForObject(
				"SELECT verification_token FROM app_users WHERE email = ?", String.class, email);
		assertThat(secondToken).isNotEqualTo(firstToken);

		// Same response for an email that was never registered — never reveals which is which.
		ResponseEntity<Void> unknownResponse = restTemplate.postForEntity("/api/auth/resend-verification",
				Map.of("tenantSlug", slug, "email", "nobody-" + slug + "@example.com"), Void.class);
		assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void forgotPasswordThenResetAllowsLoginWithNewPassword() {
		RegisteredTenant tenant = registerTenant();
		String email = tenant.slug() + "@example.com";

		ResponseEntity<Void> forgotResponse = restTemplate.postForEntity("/api/auth/forgot-password",
				Map.of("tenantSlug", tenant.slug(), "email", email), Void.class);
		assertThat(forgotResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		String resetToken = jdbcTemplate.queryForObject(
				"SELECT reset_token FROM app_users WHERE email = ?", String.class, email);
		assertThat(resetToken).isNotBlank();

		ResponseEntity<Map> resetResponse = restTemplate.postForEntity("/api/auth/reset-password",
				Map.of("tenantSlug", tenant.slug(), "token", resetToken, "newPassword", "brand-new-password"),
				Map.class);
		assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat((String) resetResponse.getBody().get("token")).isNotBlank();

		ResponseEntity<Map> loginWithOldPassword = restTemplate.postForEntity("/api/auth/login",
				Map.of("tenantSlug", tenant.slug(), "email", email, "password", "supersecret123"), Map.class);
		assertThat(loginWithOldPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<Map> loginWithNewPassword = restTemplate.postForEntity("/api/auth/login",
				Map.of("tenantSlug", tenant.slug(), "email", email, "password", "brand-new-password"), Map.class);
		assertThat(loginWithNewPassword.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void resetPasswordWithAnExpiredTokenIsRejected() {
		RegisteredTenant tenant = registerTenant();
		String email = tenant.slug() + "@example.com";
		restTemplate.postForEntity("/api/auth/forgot-password", Map.of("tenantSlug", tenant.slug(), "email", email),
				Void.class);

		jdbcTemplate.update("UPDATE app_users SET reset_token_expires_at = ? WHERE email = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), email);
		String resetToken = jdbcTemplate.queryForObject(
				"SELECT reset_token FROM app_users WHERE email = ?", String.class, email);

		ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/reset-password",
				Map.of("tenantSlug", tenant.slug(), "token", resetToken, "newPassword", "brand-new-password"),
				Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void forgotPasswordAlwaysReturnsNoContentEvenForAnUnknownEmail() {
		RegisteredTenant tenant = registerTenant();

		ResponseEntity<Void> response = restTemplate.postForEntity("/api/auth/forgot-password",
				Map.of("tenantSlug", tenant.slug(), "email", "nobody-" + tenant.slug() + "@example.com"), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	/**
	 * The actual regression this feature exists to fix: an owner whose original signup email
	 * (carrying both the verification link and the one-time password) never arrived is stuck both
	 * unverified and without a known password — forgot-password must rescue that account too, not
	 * just one that already verified and later forgot its password.
	 */
	@Test
	void resetPasswordRescuesAnAccountThatNeverVerifiedItsEmail() {
		String slug = "never-verified-" + UUID.randomUUID().toString().substring(0, 8);
		String email = slug + "@example.com";
		restTemplate.postForEntity("/api/auth/register", Map.of(
				"tenantName", "Never Verified", "tenantSlug", slug, "ownerEmail", email, "ownerPassword",
				"supersecret123"), Map.class);

		restTemplate.postForEntity("/api/auth/forgot-password", Map.of("tenantSlug", slug, "email", email),
				Void.class);
		String resetToken = jdbcTemplate.queryForObject(
				"SELECT reset_token FROM app_users WHERE email = ?", String.class, email);

		ResponseEntity<Map> resetResponse = restTemplate.postForEntity("/api/auth/reset-password",
				Map.of("tenantSlug", slug, "token", resetToken, "newPassword", "brand-new-password"), Map.class);
		assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		Boolean emailVerified = jdbcTemplate.queryForObject(
				"SELECT email_verified FROM app_users WHERE email = ?", Boolean.class, email);
		assertThat(emailVerified).isTrue();

		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login",
				Map.of("tenantSlug", slug, "email", email, "password", "brand-new-password"), Map.class);
		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
