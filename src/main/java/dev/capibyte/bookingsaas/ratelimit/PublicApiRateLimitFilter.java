package dev.capibyte.bookingsaas.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Token-bucket rate limit on the unauthenticated public booking API, keyed by client IP — there's
 * no user identity to key on there, unlike the JWT-authenticated admin API. One bucket per IP
 * covers every /api/public/** route (not tiered per-endpoint). See {@link AbstractRateLimitFilter}
 * for the shared bucket/429 mechanics and its X-Forwarded-For caveat.
 */
@Component
public class PublicApiRateLimitFilter extends AbstractRateLimitFilter {

	private static final String PUBLIC_API_PREFIX = "/api/public/";

	public PublicApiRateLimitFilter(
			@Value("${app.rate-limit.capacity}") long capacity,
			@Value("${app.rate-limit.refill-tokens}") long refillTokens,
			@Value("${app.rate-limit.refill-duration-seconds}") long refillDurationSeconds,
			ObjectMapper objectMapper) {
		super(capacity, refillTokens, refillDurationSeconds, objectMapper);
	}

	@Override
	protected boolean appliesTo(HttpServletRequest request) {
		return request.getRequestURI().startsWith(PUBLIC_API_PREFIX);
	}
}
