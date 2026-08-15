package dev.capibyte.bookingsaas.staff;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Same guarantee as StockSaleConcurrencyTest, applied to the professional plan limit: proves the
 * pessimistic lock on the tenant row (TenantService#findByIdForUpdate), not a plain read-then-write
 * in application code, is what stops more simultaneous creates than TRIAL's maxProfessionals=2 from
 * all succeeding.
 */
class ProfessionalLimitConcurrencyTest extends IntegrationTestBase {

	@Test
	void onlyAsManySimultaneousProfessionalsAsThePlanAllowsSucceed() throws Exception {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		// Tenants start on TRIAL by default (maxProfessionals=2) — no need to set the tier here.

		ResponseEntity<Map> branch = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Main"), headers), Map.class);
		String branchId = (String) branch.getBody().get("id");

		int attempts = 5;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CyclicBarrier barrier = new CyclicBarrier(attempts);
		List<Callable<HttpStatus>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			int n = i;
			tasks.add(() -> {
				barrier.await();
				ResponseEntity<Map> response = restTemplate.exchange("/api/professionals", HttpMethod.POST,
						new HttpEntity<>(Map.of("branchId", branchId, "displayName", "Pro " + n), headers), Map.class);
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

		assertThat(succeeded).as("only TRIAL's maxProfessionals should win the race").isEqualTo(2);
		assertThat(rejected).as("every other concurrent create should be rejected").isEqualTo(attempts - 2);
	}
}
