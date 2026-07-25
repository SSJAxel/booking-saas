package dev.capibyte.bookingsaas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI bookingSaasOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Booking SaaS API")
						.version("v1")
						.description("""
								Multi-tenant appointment booking platform (tattoo studios, barbershops, salons).

								Admin/staff endpoints (/api/**) require a Bearer JWT obtained from /api/auth/login \
								or /api/auth/register. The public booking API (/api/public/{tenantSlug}/**) needs \
								no auth — the tenant is resolved from the slug in the path."""))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
