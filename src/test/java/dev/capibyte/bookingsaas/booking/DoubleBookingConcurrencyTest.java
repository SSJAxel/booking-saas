package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The single most important test in this project: proves the DB-level EXCLUDE constraint (not
 * application code) is what makes double-booking structurally impossible. A sequential test can't
 * prove this — two requests could both pass a "is this slot free?" check before either commits —
 * so this fires genuinely concurrent requests at a real embedded server over real HTTP.
 */
class DoubleBookingConcurrencyTest extends IntegrationTestBase {

	@Test
	void onlyOneOfManySimultaneousBookingsForTheSameSlotSucceeds() throws Exception {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 60, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		String date = nextMonday().toString();
		String startTime = "10:00:00";

		int attempts = 10;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CyclicBarrier barrier = new CyclicBarrier(attempts);
		List<Callable<HttpStatus>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			int idx = i;
			tasks.add(() -> {
				barrier.await();
				Map<String, Object> body = Map.of(
						"professionalId", professionalId,
						"serviceId", serviceId,
						"date", date,
						"startTime", startTime,
						"clientName", "Client " + idx,
						"clientEmail", "client-" + idx + "-" + UUID.randomUUID() + "@example.com",
						"clientPhone", "+541100000" + idx);
				ResponseEntity<Map> response = restTemplate.postForEntity(
						"/api/public/" + tenant.slug() + "/appointments", body, Map.class);
				return HttpStatus.valueOf(response.getStatusCode().value());
			});
		}

		List<Future<HttpStatus>> futures = pool.invokeAll(tasks);
		long created = 0;
		long conflicted = 0;
		for (Future<HttpStatus> future : futures) {
			HttpStatus status = future.get(30, TimeUnit.SECONDS);
			if (status == HttpStatus.CREATED) {
				created++;
			} else if (status == HttpStatus.CONFLICT) {
				conflicted++;
			}
		}
		pool.shutdown();

		assertThat(created).as("exactly one concurrent booking should win").isEqualTo(1);
		assertThat(conflicted).as("every other concurrent booking should be rejected with 409").isEqualTo(attempts - 1);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}
}
