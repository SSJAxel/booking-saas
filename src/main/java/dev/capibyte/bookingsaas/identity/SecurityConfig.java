package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.booking.PublicTenantResolutionFilter;
import dev.capibyte.bookingsaas.ratelimit.AuthRateLimitFilter;
import dev.capibyte.bookingsaas.ratelimit.PublicApiRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = {
			"/api/auth/**",
			"/api/public/**",
			"/api/plans",
			"/api/webhooks/**",
			"/api/mercadopago/oauth/callback",
			"/uploads/public/**",
			"/actuator/health",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**"
	};

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final PublicTenantResolutionFilter publicTenantResolutionFilter;
	private final PublicApiRateLimitFilter publicApiRateLimitFilter;
	private final AuthRateLimitFilter authRateLimitFilter;
	private final JsonAuthenticationEntryPoint authenticationEntryPoint;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				// A custom filter class can't be used as another addFilterBefore's position
				// anchor (Spring Security requires a filter with a "registered order", i.e. one
				// of its own known filter types) — so all four are anchored at the same
				// standard filter, and rely on insertion order for their relative placement.
				// Rate limiting goes first so an over-quota request never reaches the tenant
				// lookup.
				.addFilterBefore(publicApiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(publicTenantResolutionFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// Both filters above are @Component beans (so SecurityConfig can inject them) AND wired
	// explicitly into the security chain — without disabling their auto-registration, Spring
	// Boot would ALSO register each as a generic servlet filter for every URL, running it twice
	// per request (harmless here since both are idempotent, but wasteful and worth avoiding).
	@Bean
	FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterAutoRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<PublicTenantResolutionFilter> publicTenantFilterAutoRegistration(
			PublicTenantResolutionFilter filter) {
		FilterRegistrationBean<PublicTenantResolutionFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	// Unlike the two filters above, double-registration here would NOT be harmless: consuming a
	// rate-limit token isn't idempotent, so running this filter twice per request would silently
	// halve the configured limit.
	@Bean
	FilterRegistrationBean<PublicApiRateLimitFilter> rateLimitFilterAutoRegistration(PublicApiRateLimitFilter filter) {
		FilterRegistrationBean<PublicApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterAutoRegistration(AuthRateLimitFilter filter) {
		FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}
