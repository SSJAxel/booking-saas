package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "Sobreturno": POST /api/appointments/overtime, the one path that's allowed to overlap another
 * appointment of the same professional on purpose (see V25 migration and
 * AppointmentService#book's overtime overload). Everything else — normal manual bookings and the
 * public booking flow — must keep blocking overlaps exactly as before.
 */
class AppointmentOvertimeBookingTest extends IntegrationTestBase {

	@Test
	void overtimeAppointmentOverlapsAnExistingAppointmentOfTheSameProfessional() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		Map<String, Object> original = createManual(headers, setup, nextMonday().atTime(10, 0), "original-client");
		String originalId = (String) original.get("id");

		Map<String, Object> overtime = createOvertime(headers, setup, nextMonday().atTime(10, 0), "overtime-client");

		assertThat(overtime.get("overtime")).isEqualTo(true);
		assertThat(overtime.get("status")).isEqualTo("CONFIRMED");

		ResponseEntity<Map> originalAfter = restTemplate.exchange("/api/appointments/" + originalId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(originalAfter.getBody().get("status")).isEqualTo("CONFIRMED");
	}

	@Test
	void twoOvertimeAppointmentsCanAlsoOverlapEachOther() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		createOvertime(headers, setup, nextMonday().atTime(10, 0), "overtime-1");
		Map<String, Object> second = createOvertime(headers, setup, nextMonday().atTime(10, 0), "overtime-2");

		assertThat(second.get("overtime")).isEqualTo(true);
	}

	@Test
	void normalAppointmentsStillBlockEachOtherRegression() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		createManual(headers, setup, nextMonday().atTime(10, 0), "first-client");

		Map<String, Object> body = manualBody(setup, nextMonday().atTime(10, 0), "second-client");
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void overtimeAppointmentIsNotOfferedAsAFreeSlotInPublicAvailability() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());
		Setup setup = setUpProfessionalAndService(headers);

		createOvertime(headers, setup, nextMonday().atTime(10, 0), "overtime-client");

		ResponseEntity<List> response = restTemplate.getForEntity(
				"/api/public/" + tenant.slug() + "/availability?professionalId=" + setup.professionalId()
						+ "&serviceId=" + setup.serviceId() + "&date=" + nextMonday(),
				List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> slots = response.getBody();
		assertThat(slots).noneSatisfy(slot -> assertThat(slot.get("start")).isEqualTo("10:00:00"));
	}

	private Setup setUpProfessionalAndService(HttpHeaders headers) {
		String branchId = (String) post("/api/branches", Map.of("name", "Main"), headers).get("id");
		String professionalId = (String) post("/api/professionals",
				Map.of("branchId", branchId, "displayName", "Pro"), headers).get("id");
		post("/api/professionals/" + professionalId + "/availability",
				Map.of("dayOfWeek", DayOfWeek.MONDAY.name(), "startTime", "09:00:00", "endTime", "18:00:00"), headers);
		String serviceId = (String) post("/api/services",
				Map.of("name", "Cut", "durationMinutes", 30, "price", 50.0), headers).get("id");
		restTemplate.exchange("/api/services/" + serviceId + "/professionals", HttpMethod.POST,
				new HttpEntity<>(Map.of("professionalId", professionalId), headers), Void.class);
		return new Setup(professionalId, serviceId);
	}

	private Map<String, Object> createManual(HttpHeaders headers, Setup setup, LocalDateTime at,
			String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private Map<String, Object> createOvertime(HttpHeaders headers, Setup setup, LocalDateTime at,
			String clientHandle) {
		Map<String, Object> body = manualBody(setup, at, clientHandle);
		ResponseEntity<Map> response = restTemplate.exchange("/api/appointments/overtime", HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private Map<String, Object> manualBody(Setup setup, LocalDateTime at, String clientHandle) {
		return Map.of(
				"professionalId", setup.professionalId(),
				"serviceId", setup.serviceId(),
				"date", at.toLocalDate().toString(),
				"startTime", at.toLocalTime().toString(),
				"clientName", "Client " + clientHandle,
				"clientEmail", clientHandle + "@example.com",
				"clientPhone", "+549" + String.format("%08d", Math.abs(clientHandle.hashCode()) % 100_000_000));
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
