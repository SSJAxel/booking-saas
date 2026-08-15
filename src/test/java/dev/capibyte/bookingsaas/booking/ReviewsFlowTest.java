package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Public client reviews: PRO/MAX-gated, opt-in per tenant, single-use mailed invite token issued
 * on COMPLETED (same shape as AppUserService's reset-password token, just scoped to an
 * Appointment — see ReviewService), public post-moderation (visible immediately, owner hides
 * afterward). Books through /api/appointments/overtime, same trick CommissionsFlowTest/
 * LoyaltyRewardsFlowTest use.
 */
class ReviewsFlowTest extends IntegrationTestBase {

	@Test
	void enablingReviewsIsRejectedOnPlansWithoutTheFeatureAndAllowedOnProAndMax() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		ResponseEntity<Map> onTrial = updateReviews(headers, true);
		assertThat(onTrial.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		setPlanTier(tenant, "PRO");
		ResponseEntity<Map> onPro = updateReviews(headers, true);
		assertThat(onPro.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(onPro.getBody().get("reviewsEnabled")).isEqualTo(true);
	}

	@Test
	void completingAnAppointmentWhileEnabledIssuesAReviewToken() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-a");
		completeAppointment(headers, (String) booked.get("id"));

		String token = jdbcTemplate.queryForObject("SELECT review_token FROM appointments WHERE id = ?",
				String.class, UUID.fromString((String) booked.get("id")));
		assertThat(token).isNotNull();
	}

	@Test
	void completingAnAppointmentWhileDisabledLeavesTheTokenNull() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-b");
		completeAppointment(headers, (String) booked.get("id"));

		String token = jdbcTemplate.queryForObject("SELECT review_token FROM appointments WHERE id = ?",
				String.class, UUID.fromString((String) booked.get("id")));
		assertThat(token).isNull();
	}

	@Test
	void publicInviteEndpointReturnsContextForAValidTokenAndBadRequestForGarbage() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-c");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);

		ResponseEntity<Map> invite = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/reviews/invite/" + token, Map.class);
		assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(invite.getBody().get("professionalName")).isEqualTo("Pro");
		assertThat(invite.getBody().get("serviceName")).isEqualTo("Cut");

		ResponseEntity<Map> garbage = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/reviews/invite/not-a-real-token", Map.class);
		assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void submittingCreatesAVisibleReviewClearsTheTokenAndRejectsResubmission() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-d");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);

		ResponseEntity<Void> submitted = submitReview(tenant.slug(), token, 5, "Great cut!");
		assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		String clearedToken = jdbcTemplate.queryForObject("SELECT review_token FROM appointments WHERE id = ?",
				String.class, UUID.fromString((String) booked.get("id")));
		assertThat(clearedToken).isNull();

		Integer visible = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM reviews WHERE appointment_id = ? AND visible = true", Integer.class,
				UUID.fromString((String) booked.get("id")));
		assertThat(visible).isEqualTo(1);

		ResponseEntity<Void> resubmit = submitReview(tenant.slug(), token, 1, "Second try");
		assertThat(resubmit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void expiredInviteTokenIsRejected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-e");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);

		jdbcTemplate.update("UPDATE appointments SET review_token_expires_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), UUID.fromString((String) booked.get("id")));

		ResponseEntity<Void> response = submitReview(tenant.slug(), token, 4, "Late review");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void publicListOnlyReturnsVisibleReviewsAndIsEmptyWhileDisabled() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-f");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);
		submitReview(tenant.slug(), token, 5, "Loved it");

		List<Map> visibleList = getPublicReviews(tenant.slug());
		assertThat(visibleList).hasSize(1);
		assertThat(visibleList.get(0).get("comment")).isEqualTo("Loved it");
		// Only the first name — see ReviewService#toPublicResponse.
		assertThat(visibleList.get(0).get("clientName").toString()).doesNotContain(" ");

		updateReviews(headers, false);
		assertThat(getPublicReviews(tenant.slug())).isEmpty();
	}

	@Test
	void ownerCanHideAReviewAndStaffIsForbiddenOnBothEndpoints() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-g");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);
		submitReview(tenant.slug(), token, 3, "It was fine");

		ResponseEntity<Map[]> ownerList = restTemplate.exchange("/api/reviews", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		assertThat(ownerList.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ownerList.getBody()).hasSize(1);
		String reviewId = (String) ownerList.getBody()[0].get("id");

		ResponseEntity<Map> hidden = restTemplate.exchange("/api/reviews/" + reviewId + "/visibility",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("visible", false), headers), Map.class);
		assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(hidden.getBody().get("visible")).isEqualTo(false);
		assertThat(getPublicReviews(tenant.slug())).isEmpty();

		HttpHeaders staffHeaders = createStaffAndLogin(tenant);
		ResponseEntity<Map> staffList = restTemplate.exchange("/api/reviews", HttpMethod.GET,
				new HttpEntity<>(staffHeaders), Map.class);
		assertThat(staffList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<Map> staffPatch = restTemplate.exchange("/api/reviews/" + reviewId + "/visibility",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("visible", true), staffHeaders), Map.class);
		assertThat(staffPatch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<Map> staffDelete = restTemplate.exchange("/api/reviews/" + reviewId, HttpMethod.DELETE,
				new HttpEntity<>(staffHeaders), Map.class);
		assertThat(staffDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void ownerCanPermanentlyDeleteAReview() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		setPlanTier(tenant, "PRO");
		updateReviews(headers, true);
		Setup setup = setUpProfessionalAndService(headers);
		Map<String, Object> booked = createOvertime(headers, setup, nextMonday().atTime(10, 0), "client-h");
		completeAppointment(headers, (String) booked.get("id"));
		String token = reviewTokenFor(booked);
		submitReview(tenant.slug(), token, 2, "Not great");

		ResponseEntity<Map[]> ownerList = restTemplate.exchange("/api/reviews", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		String reviewId = (String) ownerList.getBody()[0].get("id");

		ResponseEntity<Void> deleted = restTemplate.exchange("/api/reviews/" + reviewId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<Map[]> afterDelete = restTemplate.exchange("/api/reviews", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		assertThat(afterDelete.getBody()).isEmpty();

		ResponseEntity<Map> deleteAgain = restTemplate.exchange("/api/reviews/" + reviewId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Map.class);
		assertThat(deleteAgain.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private String reviewTokenFor(Map<String, Object> appointment) {
		return jdbcTemplate.queryForObject("SELECT review_token FROM appointments WHERE id = ?", String.class,
				UUID.fromString((String) appointment.get("id")));
	}

	private void setPlanTier(RegisteredTenant tenant, String planTier) {
		jdbcTemplate.update("UPDATE tenants SET plan_tier = ? WHERE slug = ?", planTier, tenant.slug());
	}

	private ResponseEntity<Map> updateReviews(HttpHeaders headers, boolean enabled) {
		return restTemplate.exchange("/api/tenant/reviews", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("enabled", enabled), headers), Map.class);
	}

	private ResponseEntity<Void> submitReview(String tenantSlug, String token, int rating, String comment) {
		return restTemplate.postForEntity("/api/public/" + tenantSlug + "/reviews/invite/" + token,
				Map.of("rating", rating, "comment", comment), Void.class);
	}

	@SuppressWarnings("unchecked")
	private List<Map> getPublicReviews(String tenantSlug) {
		ResponseEntity<Map[]> response = restTemplate.getForEntity("/api/public/" + tenantSlug + "/reviews",
				Map[].class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return List.of(response.getBody());
	}

	private HttpHeaders createStaffAndLogin(RegisteredTenant tenant) {
		String staffEmail = "staff-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update(
				"INSERT INTO app_users (id, tenant_id, email, password_hash, role, active, email_verified) "
						+ "SELECT gen_random_uuid(), tenant_id, ?, password_hash, 'STAFF', true, true FROM app_users WHERE email = ?",
				staffEmail, tenant.slug() + "@example.com");
		Map<String, Object> loginRequest = Map.of("tenantSlug", tenant.slug(), "email", staffEmail, "password",
				"supersecret123");
		ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);
		return authHeaders((String) loginResponse.getBody().get("token"));
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		return new Setup(professionalId, serviceId);
	}

	private Map<String, Object> createOvertime(HttpHeaders headers, Setup setup, LocalDateTime at,
			String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private void completeAppointment(HttpHeaders headers, String appointmentId) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/" + appointmentId + "/status",
				HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "COMPLETED"), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private Map<String, Object> manualBody(Setup setup, LocalDateTime at, String clientHandle) {
		return Map.of(
				"professionalId", setup.professionalId(),
				"serviceId", setup.serviceId(),
				"date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientHandle.hashCode()) % 100_000_000));
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceId) {
	}
}
