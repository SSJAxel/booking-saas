package dev.capibyte.bookingsaas.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Browser-based frontends (the admin panel, eventually the public booking widget on the
 * business's own site) live on a different origin than this API, so the default same-origin
 * policy blocks them unless explicitly allowed here. Origins are an explicit allow-list (not
 * "*"), driven by {@code app.cors.allowed-origins} so each deployment configures its own — no
 * code change needed to add a new frontend origin later.
 */
@Configuration
public class CorsConfig {

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Bearer tokens, not cookies — no ambient credentials sent, so no need for
		// allowCredentials(true) (which would also force a non-wildcard-but-still-broad setup).
		configuration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
