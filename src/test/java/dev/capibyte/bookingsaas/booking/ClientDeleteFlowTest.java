package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Borrado permanente de un cliente (Turnos → Lista → Clientes → "Eliminar cliente") — se lleva
 * puesto turnos y reseñas en cascada (V46). El caso de la reseña es el que importa probar: antes de
 * V46, reviews.appointment_id no tenía ON DELETE CASCADE, así que esto fallaba con una violación de
 * foreign key apenas el cliente tuviera una reseña dejada.
 */
class ClientDeleteFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void deletingAClientCascadesToItsAppointmentsAndReviews() {
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
				"clientName", "Cliente A Borrar", "clientEmail", "borrar@example.com",
				"clientPhone", "+549111222444");
		ResponseEntity<Map> created = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				body, Map.class);
		String clientId = (String) created.getBody().get("clientId");
		String appointmentId = (String) created.getBody().get("id");
		UUID tenantId = UUID
				.fromString(jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE slug = ?", String.class, tenant.slug()));

		// Directly inserted (not via the full review-invite flow) — only the cascade behavior matters
		// here, and this is the fastest way to get a real reviews row referencing this client/turno.
		UUID reviewId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO reviews (id, tenant_id, appointment_id, client_id, professional_id, rating, visible, created_at) "
						+ "VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, 5, true, now())",
				reviewId, tenantId, appointmentId, clientId, professionalId);

		ResponseEntity<Void> deleteResponse = restTemplate.exchange("/api/clients/" + clientId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM clients WHERE id = ?::uuid", Integer.class, clientId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appointments WHERE id = ?::uuid", Integer.class,
				appointmentId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reviews WHERE id = ?::uuid", Integer.class, reviewId))
				.isZero();
	}

	@Test
	void deletingAnAlreadyDeletedClientReturns404() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/clients/" + UUID.randomUUID(), HttpMethod.DELETE,
				new HttpEntity<>(headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
