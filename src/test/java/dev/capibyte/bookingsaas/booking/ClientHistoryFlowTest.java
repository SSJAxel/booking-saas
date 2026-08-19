package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * "Seguimiento de clientes" — who a client saw, when, for what, plus freeform notes. Backs
 * ClientHistoryModal.jsx.
 */
class ClientHistoryFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

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

		LocalDate firstMonday = nextMonday();
		Map<String, Object> firstBody = Map.of("professionalId", proA, "serviceId", cutId, "date",
				firstMonday.toString(), "startTime", "09:00:00", "clientName", "Repeat Client", "clientEmail",
				"repeat@example.com", "clientPhone", "+549111222333");
		ResponseEntity<Map> first = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				firstBody, Map.class);
		String clientId = (String) first.getBody().get("clientId");
		String firstAppointmentId = (String) first.getBody().get("id");

		Map<String, Object> secondBody = Map.of("professionalId", proB, "serviceId", colorId, "date",
				firstMonday.plusWeeks(1).toString(), "startTime", "10:00:00", "clientName", "Repeat Client",
				"clientEmail", "repeat@example.com", "clientPhone", "+549111222333");
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
	void ownerCanSetAndClearClientProfileFieldsOnProPlan() {
		RegisteredTenant tenant = registerTenant();
		jdbcTemplate.update("UPDATE tenants SET plan_tier = 'PRO' WHERE slug = ?", tenant.slug());
		HttpHeaders headers = authHeaders(tenant.token());
		String clientId = bookAClientAndReturnItsId(tenant, headers);

		ResponseEntity<Map> set = restTemplate.exchange("/api/clients/" + clientId + "/profile", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("notes", "Prefiere charlar poco", "servicePreferences",
						"Fade #2, sin navaja en el contorno", "allergies", "Alérgico a la tintura X"), headers),
				Map.class);
		assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(set.getBody().get("notes")).isEqualTo("Prefiere charlar poco");
		assertThat(set.getBody().get("servicePreferences")).isEqualTo("Fade #2, sin navaja en el contorno");
		assertThat(set.getBody().get("allergies")).isEqualTo("Alérgico a la tintura X");

		Map<String, Object> clearBody = new HashMap<>();
		clearBody.put("notes", null);
		clearBody.put("servicePreferences", null);
		clearBody.put("allergies", null);
		ResponseEntity<Map> cleared = restTemplate.exchange("/api/clients/" + clientId + "/profile", HttpMethod.PATCH,
				new HttpEntity<>(clearBody, headers), Map.class);
		assertThat(cleared.getBody().get("notes")).isNull();
		assertThat(cleared.getBody().get("servicePreferences")).isNull();
		assertThat(cleared.getBody().get("allergies")).isNull();
	}

	/**
	 * {@code notes} predates the PRO gate and stays free on every plan — only servicePreferences/
	 * allergies are blocked below PRO, and only when actually setting a new value (not when a form
	 * just resends whatever was already there, see ClientService#updateProfile's Javadoc).
	 */
	@Test
	void settingServicePreferencesOrAllergiesRejectedBelowProPlanButNotesStaysFree() {
		RegisteredTenant tenant = registerTenant(); // defaults to TRIAL
		HttpHeaders headers = authHeaders(tenant.token());
		String clientId = bookAClientAndReturnItsId(tenant, headers);

		ResponseEntity<Map> rejected = restTemplate.exchange("/api/clients/" + clientId + "/profile", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("servicePreferences", "Fade #2"), headers), Map.class);
		assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> notesOnly = restTemplate.exchange("/api/clients/" + clientId + "/profile", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("notes", "Cliente puntual"), headers), Map.class);
		assertThat(notesOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(notesOnly.getBody().get("notes")).isEqualTo("Cliente puntual");
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
				nextMonday().toString(), "startTime", "09:00:00", "clientName", "Client", "clientEmail",
				"notes@example.com", "clientPhone", "+549111222333");
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

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}
}
