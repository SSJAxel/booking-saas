package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
 * Covers {@code POST .../appointments/group} — booking two services in one client flow ("corte
 * con Lauti" + "tratamiento capilar con Facu"), see README "A CHEQUEAR".
 */
class BookingGroupFlowTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void bookingTwoServicesTogetherSharesTheSameBookingGroupId() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		String branchId = createBranch(headers);
		Setup lauti = setUpProfessionalAndService(branchId, headers, "Lauti", "Corte");
		Setup facu = setUpProfessionalAndService(branchId, headers, "Facu", "Tratamiento capilar");

		LocalDate monday = nextMonday();
		Map<String, Object> body = Map.of(
				"clientName", "Cliente Combo",
				"clientEmail", "combo-client@example.com",
				"clientPhone", "+5411000099",
				"items", List.of(
						item(lauti, monday, "10:00:00"),
						item(facu, monday, "11:00:00")));

		ResponseEntity<List> response = restTemplate
				.postForEntity("/api/public/" + tenant.slug() + "/appointments/group", body, List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Map<String, Object>> appointments = response.getBody();
		assertThat(appointments).hasSize(2);
		Object groupId1 = appointments.get(0).get("bookingGroupId");
		Object groupId2 = appointments.get(1).get("bookingGroupId");
		assertThat(groupId1).isNotNull().isEqualTo(groupId2);
		// Each leg keeps its own service/professional — this is scheduling only, never a merged item.
		assertThat(appointments.get(0).get("professionalId")).isEqualTo(lauti.professionalId());
		assertThat(appointments.get(1).get("professionalId")).isEqualTo(facu.professionalId());
	}

	/**
	 * If the 2nd leg collides with an already-booked slot, the whole group must roll back —
	 * including the 1st leg, already flushed earlier in the same transaction — so the client never
	 * ends up with just one of the two services booked.
	 */
	@Test
	void groupBookingRollsBackEverythingIfAnyLegCollides() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		String branchId = createBranch(headers);
		Setup lauti = setUpProfessionalAndService(branchId, headers, "Lauti", "Corte");
		Setup facu = setUpProfessionalAndService(branchId, headers, "Facu", "Tratamiento capilar");

		LocalDate monday = nextMonday();
		// Someone else takes Facu's 11:00 slot before the group request arrives.
		Map<String, Object> takenBody = Map.of(
				"professionalId", facu.professionalId(), "serviceId", facu.serviceId(),
				"date", monday.toString(), "startTime", "11:00:00",
				"clientName", "Otro cliente", "clientEmail", "otro@example.com", "clientPhone", "+5411000098");
		ResponseEntity<Map> taken = restTemplate.postForEntity("/api/public/" + tenant.slug() + "/appointments",
				takenBody, Map.class);
		assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		Map<String, Object> body = Map.of(
				"clientName", "Cliente Combo",
				"clientEmail", "combo-client-2@example.com",
				"clientPhone", "+5411000097",
				"items", List.of(
						item(lauti, monday, "10:00:00"),
						item(facu, monday, "11:00:00")));

		ResponseEntity<Map> response = restTemplate
				.postForEntity("/api/public/" + tenant.slug() + "/appointments/group", body, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		Integer countForLauti = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM appointments WHERE professional_id = ?::uuid", Integer.class,
				lauti.professionalId());
		assertThat(countForLauti).isZero();
	}

	private Map<String, Object> item(Setup setup, LocalDate date, String startTime) {
		return Map.of("professionalId", setup.professionalId(), "serviceId", setup.serviceId(), "date",
				date.toString(), "startTime", startTime);
	}

	private String createBranch(HttpHeaders headers) {
		return (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
	}

	private Setup setUpProfessionalAndService(String branchId, HttpHeaders headers, String professionalName,
			String serviceName) {
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", professionalName), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);

		String serviceId = (String) post("/api/services",
				Map.of("name", serviceName, "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);

		return new Setup(professionalId, serviceId);
	}

	private Map post(String path, Map<String, Object> body, HttpHeaders headers) {
		ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
				Map.class);
		return response.getBody();
	}

	private LocalDate nextMonday() {
		return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private record Setup(String professionalId, String serviceId) {
	}
}
