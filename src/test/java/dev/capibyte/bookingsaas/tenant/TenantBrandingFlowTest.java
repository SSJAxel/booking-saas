package dev.capibyte.bookingsaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.capibyte.bookingsaas.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantBrandingFlowTest extends IntegrationTestBase {

	@Test
	void updatingBrandingIsReflectedOnTheAdminAndPublicTenantEndpoints() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("logoUrl", "https://example.com/logo.png", "accentColor", "#FF5733", "tagline",
						"Cortes con estilo"), headers),
				Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().get("logoUrl")).isEqualTo("https://example.com/logo.png");
		assertThat(response.getBody().get("accentColor")).isEqualTo("#FF5733");
		assertThat(response.getBody().get("tagline")).isEqualTo("Cortes con estilo");

		ResponseEntity<Map> publicResponse =
				restTemplate.getForEntity("/api/public/" + tenant.slug(), Map.class);
		assertThat(publicResponse.getBody().get("logoUrl")).isEqualTo("https://example.com/logo.png");
		assertThat(publicResponse.getBody().get("accentColor")).isEqualTo("#FF5733");
		assertThat(publicResponse.getBody().get("tagline")).isEqualTo("Cortes con estilo");
	}

	@Test
	void rejectsAMalformedAccentColor() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("accentColor", "not-a-color"), headers), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsAJavascriptSchemeOnSocialAndFeedUrls() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> instagram = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("instagramUrl", "javascript:alert(document.cookie)"), headers), Map.class);
		assertThat(instagram.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> facebook = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("facebookUrl", "javascript:alert(document.cookie)"), headers), Map.class);
		assertThat(facebook.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<Map> feed = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("instagramFeedUrl", "javascript:alert(document.cookie)"), headers), Map.class);
		assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void acceptsHttpsSocialAndFeedUrls() {
		RegisteredTenant tenant = registerTenant();
		HttpHeaders headers = authHeaders(tenant.token());

		ResponseEntity<Map> response = restTemplate.exchange("/api/tenant/branding", HttpMethod.PATCH,
				new HttpEntity<>(Map.of(
						"instagramUrl", "https://instagram.com/mystudio",
						"facebookUrl", "https://facebook.com/mystudio",
						"instagramFeedUrl", "https://cdn.trustindex.io/widget.js"), headers),
				Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().get("instagramUrl")).isEqualTo("https://instagram.com/mystudio");
		assertThat(response.getBody().get("facebookUrl")).isEqualTo("https://facebook.com/mystudio");
		assertThat(response.getBody().get("instagramFeedUrl")).isEqualTo("https://cdn.trustindex.io/widget.js");
	}

	@Test
	void unbrandedTenantHasNullBrandingFields() {
		String slug = "unbranded-" + UUID.randomUUID().toString().substring(0, 8);
		restTemplate.postForEntity("/api/auth/register", Map.of(
				"tenantName", "Unbranded",
				"tenantSlug", slug,
				"ownerEmail", slug + "@example.com",
				"ownerPassword", "supersecret123"), Map.class);

		ResponseEntity<Map> response = restTemplate.getForEntity("/api/public/" + slug, Map.class);

		assertThat(response.getBody().get("logoUrl")).isNull();
		assertThat(response.getBody().get("accentColor")).isNull();
		assertThat(response.getBody().get("tagline")).isNull();
	}
}
