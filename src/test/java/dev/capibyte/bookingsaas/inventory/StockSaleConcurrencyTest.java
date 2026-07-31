package dev.capibyte.bookingsaas.inventory;

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
 * Same guarantee as DoubleBookingConcurrencyTest, applied to stock: proves the atomic conditional
 * UPDATE (not a read-then-write in application code) is what stops two simultaneous sales from
 * both succeeding against the last unit.
 */
class StockSaleConcurrencyTest extends IntegrationTestBase {

	@Test
	void onlyAsManySimultaneousSalesAsAvailableStockSucceed() throws Exception {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> created = restTemplate.exchange("/api/products", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Last Unit Serum", "price", 25.0, "stock", 1), headers), Map.class);
		String productId = (String) created.getBody().get("id");

		int attempts = 10;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CyclicBarrier barrier = new CyclicBarrier(attempts);
		List<Callable<HttpStatus>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			tasks.add(() -> {
				barrier.await();
				ResponseEntity<Map> response = restTemplate.exchange("/api/sales", HttpMethod.POST,
						new HttpEntity<>(Map.of("productId", productId, "quantity", 1), headers), Map.class);
				return HttpStatus.valueOf(response.getStatusCode().value());
			});
		}

		List<Future<HttpStatus>> futures = pool.invokeAll(tasks);
		long succeeded = 0;
		long conflicted = 0;
		for (Future<HttpStatus> future : futures) {
			HttpStatus status = future.get(30, TimeUnit.SECONDS);
			if (status == HttpStatus.CREATED) {
				succeeded++;
			} else if (status == HttpStatus.CONFLICT) {
				conflicted++;
			}
		}
		pool.shutdown();

		assertThat(succeeded).as("exactly one concurrent sale should win the last unit").isEqualTo(1);
		assertThat(conflicted).as("every other concurrent sale should be rejected with 409").isEqualTo(attempts - 1);
	}
}
