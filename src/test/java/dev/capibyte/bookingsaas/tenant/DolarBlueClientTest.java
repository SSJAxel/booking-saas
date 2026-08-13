package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.capibyte.bookingsaas.tenant.dto.DolarBlueRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Same {@code MockRestServiceServer} pattern as {@code MercadoPagoClientTest} — no real network
 * call, verifies the request URL and response parsing against DolarAPI's documented shape. */
class DolarBlueClientTest {

	private MockRestServiceServer mockServer;
	private DolarBlueClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new DolarBlueClient(builder, "https://dolarapi.com");
	}

	@Test
	void fetchBlueRateParsesCompraAndVentaAndIgnoresExtraFields() {
		mockServer.expect(requestTo("https://dolarapi.com/v1/dolares/blue"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						"""
						{"compra":1580,"venta":1600,"casa":"blue","nombre":"Blue","moneda":"USD","fechaActualizacion":"2026-08-11T18:00:00.000Z"}
						""",
						MediaType.APPLICATION_JSON));

		DolarBlueRate rate = client.fetchBlueRate();

		assertThat(rate.compra()).isEqualByComparingTo(new BigDecimal("1580"));
		assertThat(rate.venta()).isEqualByComparingTo(new BigDecimal("1600"));
		mockServer.verify();
	}
}
