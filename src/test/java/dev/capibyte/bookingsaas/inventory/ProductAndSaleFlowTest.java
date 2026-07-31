package dev.capibyte.bookingsaas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProductAndSaleFlowTest extends IntegrationTestBase {

	@Test
	void sellingAProductDecrementsStockByTheSoldQuantity() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String productId = (String) createProduct(headers, "Wax", 15.0, 10).get("id");

		ResponseEntity<Map> saleResponse = restTemplate.exchange("/api/sales", HttpMethod.POST,
				new HttpEntity<>(Map.of("productId", productId, "quantity", 3), headers), Map.class);
		assertThat(saleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(new BigDecimal(saleResponse.getBody().get("amount").toString())).isEqualByComparingTo("45.00");

		Map<String, Object> product = getProduct(productId, headers);
		assertThat(product.get("stock")).isEqualTo(7);
	}

	@Test
	void sellingMoreThanAvailableStockIsRejected() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		String productId = (String) createProduct(headers, "Gel", 10.0, 2).get("id");

		ResponseEntity<Map> response = restTemplate.exchange("/api/sales", HttpMethod.POST,
				new HttpEntity<>(Map.of("productId", productId, "quantity", 3), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(getProduct(productId, headers).get("stock")).isEqualTo(2);
	}

	@Test
	void basicPlanRejectsTheSixthActiveProduct() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		for (int i = 0; i < 5; i++) {
			ResponseEntity<Map> response = restTemplate.exchange("/api/products", HttpMethod.POST,
					new HttpEntity<>(Map.of("name", "Product " + i, "price", 10.0, "stock", 1), headers), Map.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		ResponseEntity<Map> sixth = restTemplate.exchange("/api/products", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Product 5", "price", 10.0, "stock", 1), headers), Map.class);
		assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void proPlanHasNoProductLimit() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		ResponseEntity<Map> planChange = restTemplate.exchange("/api/tenant/plan", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("planTier", "PRO"), headers), Map.class);
		assertThat(planChange.getStatusCode()).isEqualTo(HttpStatus.OK);

		for (int i = 0; i < 6; i++) {
			ResponseEntity<Map> response = restTemplate.exchange("/api/products", HttpMethod.POST,
					new HttpEntity<>(Map.of("name", "Product " + i, "price", 10.0, "stock", 1), headers), Map.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}
	}

	private Map createProduct(HttpHeaders headers, String name, double price, int stock) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/products", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", name, "price", price, "stock", stock), headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private Map<String, Object> getProduct(String id, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange("/api/products/" + id, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		return response.getBody();
	}
}
