package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers the two gaps a barbershop-style tenant (no approval workflow, just direct booking) hits
 * with the appointment/payment state machine: a service with no deposit should confirm itself
 * instead of sitting in PENDING forever, and a service that does require one shouldn't let an
 * abandoned checkout squat the slot indefinitely.
 */
class AppointmentBookingFlowTest extends IntegrationTestBase {

	@Autowired
	private PendingDepositExpirationScheduler expirationScheduler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${app.booking.deposit-expiration-minutes}")
	private long expirationMinutes;

	@Test
	void bookingServiceWithoutDepositConfirmsImmediately() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(tenant, headers, null);

		Map<String, Object> booked = book(tenant.slug(), setup.professionalId(), setup.serviceId(),
				nextMonday().atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant(), "no-deposit-client");

		assertThat(booked.get("status")).isEqualTo("CONFIRMED");
		assertThat(booked.get("paymentStatus")).isEqualTo("NOT_REQUIRED");
	}

	@Test
	void unpaidDepositExpiresAndFreesTheSlot() {
		RegisteredTenant tenant = registerTenant();
		// The auto-expiration scheduler only runs for MercadoPago-enabled plans (see
		// PendingDepositExpirationScheduler's Javadoc) — a fresh tenant defaults to TRIAL, which
		// isn't one, so this test needs to opt in explicitly to exercise that path.
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(tenant, headers, 20.0);

		Instant slot = nextMonday().atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant();
		Map<String, Object> booked = book(tenant.slug(), setup.professionalId(), setup.serviceId(), slot,
				"unpaid-client");
		assertThat(booked.get("status")).isEqualTo("PENDING");
		assertThat(booked.get("paymentStatus")).isEqualTo("PENDING");
		String appointmentId = (String) booked.get("id");

		// createdAt is JPA-non-updatable by design (see Appointment's Javadoc), so backdating it to
		// simulate an old, abandoned checkout has to go around the entity layer.
		Instant longAgo = Instant.now().minus(expirationMinutes + 5, ChronoUnit.MINUTES);
		jdbcTemplate.update("UPDATE appointments SET created_at = ? WHERE id = ?::uuid", Timestamp.from(longAgo),
				appointmentId);

		expirationScheduler.expireUnpaidAppointments();

		ResponseEntity<Map> afterExpiration = restTemplate.exchange("/api/appointments/" + appointmentId,
				HttpMethod.GET, new HttpEntity<>(headers), Map.class);
		assertThat(afterExpiration.getBody().get("status")).isEqualTo("CANCELLED");

		// The slot only really counts as freed if someone else can now book over it — the EXCLUDE
		// constraint is the actual source of truth, not the status field alone.
		Map<String, Object> rebooked = book(tenant.slug(), setup.professionalId(), setup.serviceId(), slot,
				"second-client");
		assertThat(rebooked.get("status")).isEqualTo("PENDING");
	}

	/**
	 * A tenant without MercadoPago (TRIAL/PERSONAL/BASIC) can only take a deposit via the owner
	 * manually confirming a bank transfer — there's no live checkout session to "abandon", so the
	 * scheduler must leave these alone no matter how old they are. The owner decides when it's
	 * been too long and cancels by hand (a real business need: a barbershop using only a transfer
	 * alias, checking once per shift instead of every 30 minutes).
	 */
	@Test
	void unpaidDepositNeverExpiresWithoutMercadoPago() {
		RegisteredTenant tenant = registerTenant(); // defaults to TRIAL, mercadoPagoEnabled=false
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(tenant, headers, 20.0);

		Instant slot = nextMonday().atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant();
		Map<String, Object> booked = book(tenant.slug(), setup.professionalId(), setup.serviceId(), slot,
				"alias-only-client");
		String appointmentId = (String) booked.get("id");

		Instant longAgo = Instant.now().minus(expirationMinutes + 5, ChronoUnit.MINUTES);
		jdbcTemplate.update("UPDATE appointments SET created_at = ? WHERE id = ?::uuid", Timestamp.from(longAgo),
				appointmentId);

		expirationScheduler.expireUnpaidAppointments();

		ResponseEntity<Map> afterScheduler = restTemplate.exchange("/api/appointments/" + appointmentId,
				HttpMethod.GET, new HttpEntity<>(headers), Map.class);
		assertThat(afterScheduler.getBody().get("status")).isEqualTo("PENDING");
	}

	/**
	 * The expiration cutoff is per-tenant ({@code PATCH /api/tenant/deposit-expiration}), not the
	 * platform default — a MercadoPago-enabled tenant that raises its own window past the default
	 * 30 minutes must not have an appointment younger than *its own* cutoff expired.
	 */
	@Test
	void unpaidDepositRespectsTheTenantsOwnCustomWindowNotThePlatformDefault() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());
		restTemplate.exchange("/api/tenant/deposit-expiration", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("depositExpirationMinutes", 60), headers), Void.class);
		Setup setup = setUpProfessionalAndService(tenant, headers, 20.0);

		Instant slot = nextMonday().atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant();
		Map<String, Object> booked = book(tenant.slug(), setup.professionalId(), setup.serviceId(), slot,
				"custom-window-client");
		String appointmentId = (String) booked.get("id");

		// Older than the platform default (30 min) but younger than this tenant's own 60-minute window.
		Instant thirtyFiveMinAgo = Instant.now().minus(35, ChronoUnit.MINUTES);
		jdbcTemplate.update("UPDATE appointments SET created_at = ? WHERE id = ?::uuid",
				Timestamp.from(thirtyFiveMinAgo), appointmentId);

		expirationScheduler.expireUnpaidAppointments();

		ResponseEntity<Map> afterScheduler = restTemplate.exchange("/api/appointments/" + appointmentId,
				HttpMethod.GET, new HttpEntity<>(headers), Map.class);
		assertThat(afterScheduler.getBody().get("status")).isEqualTo("PENDING");
	}

	private Setup setUpProfessionalAndService(RegisteredTenant tenant, HttpHeaders headers, Double depositAmount) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);

		Map<String, Object> serviceRequest = depositAmount == null
				? Map.of("name", "Cut", "durationMinutes", 60, "price", 50.0)
				: Map.of("name", "Cut", "durationMinutes", 60, "price", 50.0, "depositAmount", depositAmount);
		String serviceId = (String) post("/api/services", serviceRequest, headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		return new Setup(professionalId, serviceId);
	}

	private Map<String, Object> book(String tenantSlug, String professionalId, String serviceId, Instant startTime,
			String clientHandle) {
		// Test tenants stay on the default UTC timezone (nothing here calls PATCH /api/tenant/timezone),
		// so deriving the wall-clock date/time fields via UTC matches what the server will resolve.
		Map<String, Object> body = Map.of(
				"professionalId", professionalId,
				"serviceId", serviceId,
				"date", LocalDate.ofInstant(startTime, ZoneOffset.UTC).toString(),
				"startTime", LocalTime.ofInstant(startTime, ZoneOffset.UTC).toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				"clientPhone", "+5411000000");
		ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/" + tenantSlug + "/appointments",
				body, Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
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
