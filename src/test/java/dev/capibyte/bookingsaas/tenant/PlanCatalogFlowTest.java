package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlanCatalogFlowTest extends IntegrationTestBase {

	@Test
	void listsAllFiveTiersWithNoAuthenticationRequired() {
		ResponseEntity<List> response = restTemplate.getForEntity("/api/plans", List.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> plans = response.getBody();
		assertThat(plans).extracting(p -> p.get("tier"))
				.containsExactlyInAnyOrder("TRIAL", "PERSONAL", "BASIC", "PRO", "MAX");

		Map<String, Object> trial = plan(plans, "TRIAL");
		assertThat(((Number) trial.get("monthlyPrice")).doubleValue()).isZero();
		assertThat(trial.get("maxProfessionals")).isEqualTo(4);
		assertThat(trial.get("maxProducts")).isEqualTo(5);
		assertThat(trial.get("maxBranches")).isEqualTo(2);
		assertThat(trial.get("maxServices")).isEqualTo(6);
		assertThat(trial.get("maxAppointmentsPerWeek")).isNull();
		assertThat(trial.get("mercadoPagoEnabled")).isEqualTo(false);
		assertThat(trial.get("whatsappEnabled")).isEqualTo(true);

		Map<String, Object> personal = plan(plans, "PERSONAL");
		assertThat(personal.get("monthlyPrice")).isNull();
		assertThat(personal.get("maxProfessionals")).isEqualTo(1);
		assertThat(personal.get("maxProducts")).isEqualTo(0);
		assertThat(personal.get("maxBranches")).isEqualTo(1);
		assertThat(personal.get("maxServices")).isEqualTo(3);
		assertThat(personal.get("maxAppointmentsPerWeek")).isEqualTo(20);
		assertThat(personal.get("mercadoPagoEnabled")).isEqualTo(false);
		assertThat(personal.get("whatsappEnabled")).isEqualTo(false);

		Map<String, Object> basic = plan(plans, "BASIC");
		assertThat(basic.get("monthlyPrice")).isNull();
		assertThat(basic.get("maxProfessionals")).isEqualTo(4);
		assertThat(basic.get("maxProducts")).isEqualTo(5);
		assertThat(basic.get("maxBranches")).isEqualTo(2);
		assertThat(basic.get("maxServices")).isEqualTo(6);
		assertThat(basic.get("maxAppointmentsPerWeek")).isNull();
		assertThat(basic.get("mercadoPagoEnabled")).isEqualTo(false);
		assertThat(basic.get("whatsappEnabled")).isEqualTo(true);

		Map<String, Object> pro = plan(plans, "PRO");
		assertThat(((Number) pro.get("monthlyPrice")).doubleValue()).isGreaterThan(0);
		assertThat(pro.get("maxProfessionals")).isEqualTo(10);
		assertThat(pro.get("maxProducts")).isEqualTo(10);
		assertThat(pro.get("maxBranches")).isEqualTo(4);
		assertThat(pro.get("maxServices")).isEqualTo(8);
		assertThat(pro.get("maxAppointmentsPerWeek")).isNull();
		assertThat(pro.get("mercadoPagoEnabled")).isEqualTo(true);
		assertThat(pro.get("whatsappEnabled")).isEqualTo(true);

		Map<String, Object> max = plan(plans, "MAX");
		assertThat(max.get("monthlyPrice")).isNull();
		assertThat(max.get("maxProfessionals")).isEqualTo(20);
		assertThat(max.get("maxProducts")).isEqualTo(20);
		assertThat(max.get("maxBranches")).isEqualTo(8);
		assertThat(max.get("maxServices")).isEqualTo(12);
		assertThat(max.get("maxAppointmentsPerWeek")).isNull();
		assertThat(max.get("mercadoPagoEnabled")).isEqualTo(true);
		assertThat(max.get("whatsappEnabled")).isEqualTo(true);
	}

	private Map<String, Object> plan(List<Map<String, Object>> plans, String tier) {
		return plans.stream().filter(p -> tier.equals(p.get("tier"))).findFirst().orElseThrow();
	}
}
