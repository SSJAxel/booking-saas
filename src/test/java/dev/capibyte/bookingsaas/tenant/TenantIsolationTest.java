package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Proves @TenantId actually isolates tenants end to end, not just in theory. */
class TenantIsolationTest extends IntegrationTestBase {

	@Test
	void tenantCannotReadAnotherTenantsBranchById() {
		RegisteredTenant tenantA = registerTenant();
		RegisteredTenant tenantB = registerTenant();

		Map<String, Object> branchRequest = Map.of("name", "Main Studio", "address", "Somewhere 123", "phone", "+541100000000");
		ResponseEntity<Map> created = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(branchRequest, authHeaders(tenantA.token())), Map.class);
		String branchId = (String) created.getBody().get("id");

		ResponseEntity<Map> crossTenantRead = restTemplate.exchange("/api/branches/" + branchId, HttpMethod.GET,
				new HttpEntity<>(authHeaders(tenantB.token())), Map.class);
		assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<Map> ownTenantRead = restTemplate.exchange("/api/branches/" + branchId, HttpMethod.GET,
				new HttpEntity<>(authHeaders(tenantA.token())), Map.class);
		assertThat(ownTenantRead.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void tenantCannotDeleteAnotherTenantsBranch() {
		RegisteredTenant tenantA = registerTenant();
		RegisteredTenant tenantB = registerTenant();

		ResponseEntity<Map> created = restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "Main"), authHeaders(tenantA.token())), Map.class);
		String branchId = (String) created.getBody().get("id");

		ResponseEntity<Void> delete = restTemplate.exchange("/api/branches/" + branchId, HttpMethod.DELETE,
				new HttpEntity<>(authHeaders(tenantB.token())), Void.class);

		assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void listEndpointOnlyReturnsTheCallersOwnData() {
		RegisteredTenant tenantA = registerTenant();
		RegisteredTenant tenantB = registerTenant();
		restTemplate.exchange("/api/branches", HttpMethod.POST,
				new HttpEntity<>(Map.of("name", "A's Branch"), authHeaders(tenantA.token())), Map.class);

		ResponseEntity<List> bList = restTemplate.exchange("/api/branches", HttpMethod.GET,
				new HttpEntity<>(authHeaders(tenantB.token())), List.class);

		assertThat(bList.getBody()).isEmpty();
	}
}
