package dev.capibyte.bookingsaas.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Local dev default is http://localhost:5173 (see application.yml app.cors.allowed-origins). */
class CorsConfigTest extends IntegrationTestBase {

	@Test
	void preflightFromAnAllowedOriginIsAccepted() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ORIGIN, "http://localhost:5173");
		headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");

		ResponseEntity<Void> response = restTemplate.exchange("/api/branches", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
	}

	@Test
	void preflightFromADisallowedOriginIsRejected() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ORIGIN, "https://evil.example.com");
		headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");

		ResponseEntity<Void> response = restTemplate.exchange("/api/branches", HttpMethod.OPTIONS,
				new HttpEntity<>(headers), Void.class);

		assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
	}
}
