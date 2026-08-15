package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "Seguimiento de clientes" — who a client saw, when, for what, plus freeform notes. Backs
 * ClientHistoryModal.jsx.
 */
class ClientHistoryFlowTest extends IntegrationTestBase {

	@Test
	void historyListsEveryVisitMostRecentFirstWithServiceAndProfessionalNames() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String proA = (String) post("/api/professionals", Map.of("branchId", branchId, "displayName", "Pro A"),
				headers).get("id");
		String proB = (String) post("/api/professionals", Map.of("branchId", branchId, "displayName", "Pro B"),
				headers).get("id");
		post("/api/professionals/" + proA + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		post("/api/professionals/" + proB + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String cutId = (String) post("/api/services", Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0),
				headers).get("id");
		String colorId = (String) post("/api/services",
				Map.of("name", "Color", "durationMinutes", 30, "price", 80.0), headers).get("id");
		assign(cutId, proA, headers);
		assign(colorId, proB, headers);

		Map<String, Object> firstBody = Map.of("professionalId", proA, "serviceId", cutId, "date", "2026-08-17",
				"startTime", "09:00:00", "clientName", "Repeat Client", "clientEmail", "repeat@example.com",
				"clientPhone", "+549111222333");
		ResponseEntity<Map> first = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				firstBody, Map.class);
		String clientId = (String) first.getBody().get("clientId");
		String firstAppointmentId = (String) first.getBody().get("id");

		Map<String, Object> secondBody = Map.of("professionalId", proB, "serviceId", colorId, "date", "2026-08-24",
				"startTime", "10:00:00", "clientName", "Repeat Client", "clientEmail", "repeat@example.com",
				"clientPhone", "+549111222333");
		restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments", secondBody, Map.class);

		restTemplate.exchange("/api/appointments/" + firstAppointmentId + "/status", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("status", "COMPLETED"), headers), Map.class);

		ResponseEntity<Map[]> history = restTemplate.exchange("/api/clients/" + clientId + "/history", HttpMethod.GET,
				new HttpEntity<>(headers), Map[].class);

		assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(history.getBody()).hasSize(2);
		// Most recent first — the second (Color, Pro B) booking was for a later date. No deposit
		// configured on either service, so a fresh booking auto-confirms rather than staying PENDING.
		assertThat(history.getBody()[0].get("serviceName")).isEqualTo("Color");
		assertThat(history.getBody()[0].get("professionalName")).isEqualTo("Pro B");
		assertThat(history.getBody()[0].get("status")).isEqualTo("CONFIRMED");
		assertThat(history.getBody()[1].get("serviceName")).isEqualTo("Cut");
		assertThat(history.getBody()[1].get("professionalName")).isEqualTo("Pro A");
		assertThat(history.getBody()[1].get("status")).isEqualTo("COMPLETED");
	}

	@Test
	void ownerCanSetAndClearClientNotes() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		String clientId = bookAClientAndReturnItsId(tenant, headers);

		ResponseEntity<Map> set = restTemplate.exchange("/api/clients/" + clientId + "/notes", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("notes", "Alérgico a la tintura X"), headers), Map.class);
		assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(set.getBody().get("notes")).isEqualTo("Alérgico a la tintura X");

		ResponseEntity<Map> cleared = restTemplate.exchange("/api/clients/" + clientId + "/notes", HttpMethod.PATCH,
				new HttpEntity<>(java.util.Collections.singletonMap("notes", null), headers), Map.class);
		assertThat(cleared.getBody().get("notes")).isNull();
	}

	private String bookAClientAndReturnItsId(RegisteredTenant tenant, HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services", Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0),
				headers).get("id");
		assign(serviceId, professionalId, headers);

		Map<String, Object> body = Map.of("professionalId", professionalId, "serviceId", serviceId, "date",
				"2026-08-17", "startTime", "09:00:00", "clientName", "Client", "clientEmail", "notes@example.com",
				"clientPhone", "+549111222333");
		ResponseEntity<Map> created = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				body, Map.class);
		return (String) created.getBody().get("clientId");
	}

	private void assign(String serviceId, String professionalId, HttpHeaders headers) {
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}
}
