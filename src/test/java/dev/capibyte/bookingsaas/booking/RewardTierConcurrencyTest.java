package dev.capibyte.bookingsaas.booking;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Same guarantee as ProfessionalLimitConcurrencyTest, applied to reward tiers: proves the reused
 * pessimistic lock on the tenant row (TenantService#findByIdForUpdate, taken by
 * RewardTierService#create) stops more than 5 simultaneous tier creations from all succeeding —
 * not just the four sites the lock was originally written for earlier this session.
 */
class RewardTierConcurrencyTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void onlyFiveOfSeveralSimultaneousTierCreationsSucceed() throws Exception {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		restTemplate.exchange("/api/tenant/loyalty-rewards", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("enabled", true, "pointsCap", 100), headers), Map.class);

		int attempts = 8;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CyclicBarrier barrier = new CyclicBarrier(attempts);
		List<Callable<HttpStatus>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			int n = i;
			tasks.add(() -> {
				barrier.await();
				ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/loyalty-rewards/tiers",
						HttpMethod.POST,
						new HttpEntity<>(Map.of("pointsRequired", n + 1, "description", "Tier " + n), headers),
						Map.class);
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

		assertThat(succeeded).as("only 5 reward tiers should win the race").isEqualTo(5);
		assertThat(rejected).as("every other concurrent create should be rejected").isEqualTo(attempts - 5);
	}
}
