package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Same guarantee as StockSaleConcurrencyTest, applied to PERSONAL's weekly-appointment cap: proves
 * the pessimistic lock on the tenant row (TenantService#findByIdForUpdate) stops more simultaneous
 * bookings than maxAppointmentsPerWeek=20 from all succeeding. Books through
 * POST /api/appointments/overtime, same as WeeklyAppointmentLimitFlowTest and for the same
 * reason — it bypasses the no_double_booking EXCLUDE constraint entirely, so the same
 * professional/slot can be reused by every concurrent request, isolating the weekly cap as the only
 * possible rejection reason.
 */
class WeeklyAppointmentLimitConcurrencyTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void onlyOneOfSeveralSimultaneousBookingsAtTheCapSucceeds() throws Exception {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PERSONAL' WHERE slug = ?", tenant.slug());
		Setup setup = setUpProfessionalAndService(headers);
		LocalDateTime slot = nextMonday().atTime(10, 0);

		for (int i = 0; i < 19; i++) {
			createOvertime(headers, setup, slot, "seed-" + i);
		}

		int attempts = 5;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CyclicBarrier barrier = new CyclicBarrier(attempts);
		List<Callable<HttpStatus>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			int n = i;
			tasks.add(() -> {
				barrier.await();
				ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
						new HttpEntity<>(manualBody(setup, slot, "race-" + n), headers), Map.class);
				return HttpStatus.valueOf(response.getStatusCode().value());
			});
		}

		List<Future<HttpStatus>> futures = pool.invokeAll(tasks);
		long succeeded = 0;
		long rejected = 0;
		for (Future<HttpStatus> future : futures) {
			HttpStatus status = future.get(30, TimeUnit.SECONDS);
			if (status == HttpStatus.CREATED) {
				succeeded++;
			} else if (status == HttpStatus.BAD_REQUEST) {
				rejected++;
			}
		}
		pool.shutdown();

		assertThat(succeeded).as("only the 20th appointment this week should win the race").isEqualTo(1);
		assertThat(rejected).as("every other concurrent booking should be rejected").isEqualTo(attempts - 1);
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

	private void createOvertime(HttpHeaders headers, Setup setup, LocalDateTime at, String clientHandle) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
				new HttpEntity<>(manualBody(setup, at, clientHandle), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
