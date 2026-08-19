package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * "Cliente fijo": an owner can search for an existing client and pin them into the Mejores
 * clientes panel regardless of their calculated rating (see Client's Javadoc). Covers the search
 * endpoint (name/email partial match) and that GET /api/reports/clients reflects the pinned flag.
 */
class ClientPinFlowTest extends IntegrationTestBase {

	@Test
	void ownerCanFindAndPinAClientRegardlessOfRating() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		Map<String, Object> body = Map.of(
				"professionalId", professionalId, "serviceId", serviceId,
				"date", nextMonday().toString(), "startTime", "09:00:00",
				"clientName", "Cliente Fiel De Toda La Vida", "clientEmail", "fiel@example.com",
				"clientPhone", "+549111222333");
		ResponseEntity<Map> created = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				body, Map.class);
		String clientId = (String) created.getBody().get("clientId");

		// Search by a partial, differently-cased name match.
		ResponseEntity<Map[]> searchResponse = restTemplate.exchange("/api/clients/search?q=fiel de", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		assertThat(searchResponse.getBody()).hasSize(1);
		assertThat(searchResponse.getBody()[0].get("id")).isEqualTo(clientId);
		assertThat(searchResponse.getBody()[0].get("pinned")).isEqualTo(false);

		ResponseEntity<Map> pinResponse = restTemplate.exchange("/api/clients/" + clientId + "/pin", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("pinned", true), headers), Map.class);
		assertThat(pinResponse.getBody().get("pinned")).isEqualTo(true);

		// This client never had a completed appointment, so rating is 0 — pinning must surface them
		// in the client stats regardless (the frontend decides ranking-panel inclusion from here).
		ResponseEntity<Map[]> statsResponse = restTemplate.exchange("/api/reports/clients", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		Map clientStats = findByClientId(statsResponse.getBody(), clientId);
		assertThat(clientStats.get("pinned")).isEqualTo(true);
		assertThat(clientStats.get("rating")).isEqualTo(0);

		// Unpin.
		ResponseEntity<Map> unpinResponse = restTemplate.exchange("/api/clients/" + clientId + "/pin", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("pinned", false), headers), Map.class);
		assertThat(unpinResponse.getBody().get("pinned")).isEqualTo(false);
	}

	@Test
	void blankSearchQueryReturnsNothing() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map[]> response = restTemplate.exchange("/api/clients/search?q=", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);
		assertThat(response.getBody()).isEmpty();
	}

	private Map findByClientId(Map[] clientStats, String clientId) {
		for (Map stats : clientStats) {
			if (clientId.equals(stats.get("clientId"))) return stats;
		}
		throw new AssertionError("No client stats found for " + clientId);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}
}
