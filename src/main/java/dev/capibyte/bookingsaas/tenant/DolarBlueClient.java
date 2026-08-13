package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.tenant.dto.DolarBlueRate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over DolarAPI's public, unauthenticated dólar-blue endpoint — same "plain
 * {@link RestClient}, no SDK" style as {@code MercadoPagoClient}. Used by
 * {@code PlanPricingScheduler} to decide whether plan prices need reindexing.
 */
@Component
public class DolarBlueClient {

	private final RestClient restClient;

	public DolarBlueClient(RestClient.Builder builder, @Value("${app.dolarapi.base-url:https://dolarapi.com}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public DolarBlueRate fetchBlueRate() {
		return restClient.get()
				.uri("/v1/dolares/blue")
				.retrieve()
				.body(DolarBlueRate.class);
	}
}
