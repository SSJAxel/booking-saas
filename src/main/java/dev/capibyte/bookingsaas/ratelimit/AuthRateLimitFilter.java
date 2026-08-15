package dev.capibyte.bookingsaas.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Token-bucket rate limit on the unauthenticated /api/auth/** surface, keyed by client IP —
 * without this, login (8-char password minimum, no account lockout), forgot-password, and
 * resend-verification could be hammered at unlimited speed from a single IP. Deliberately its own
 * filter with its own (much stricter) bucket cache rather than folding into
 * {@link PublicApiRateLimitFilter}'s /api/public/** limit, which is tuned for normal booking-page
 * browsing traffic, not credential guessing. See {@link AbstractRateLimitFilter} for the shared
 * bucket/429 mechanics and its X-Forwarded-For caveat.
 */
@Component
public class AuthRateLimitFilter extends AbstractRateLimitFilter {

	private static final String AUTH_API_PREFIX = "/api/auth/";

	public AuthRateLimitFilter(
			@Value("${app.rate-limit.auth.capacity}") long capacity,
			@Value("${app.rate-limit.auth.refill-tokens}") long refillTokens,
			@Value("${app.rate-limit.auth.refill-duration-seconds}") long refillDurationSeconds,
			ObjectMapper objectMapper) {
		super(capacity, refillTokens, refillDurationSeconds, objectMapper);
	}

	@Override
	protected boolean appliesTo(HttpServletRequest request) {
		return request.getRequestURI().startsWith(AUTH_API_PREFIX);
	}
}
