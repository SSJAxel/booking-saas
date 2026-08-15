package dev.capibyte.bookingsaas.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.capibyte.bookingsaas.common.ApiError;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared token-bucket rate-limiting logic, keyed by client IP, extracted from what was originally
 * only {@code PublicApiRateLimitFilter} — {@code AuthRateLimitFilter} needs the identical
 * bucket/429 mechanics but its own (stricter) capacity and its own bucket cache, since sharing one
 * cache across two URL scopes would let traffic on one starve the other's quota.
 *
 * Caveat (inherited by every subclass): trusts X-Forwarded-For if present, which only makes sense
 * behind a reverse proxy that sets/overwrites that header itself — see the original filter's
 * Javadoc for why this is fine for this project's scope.
 */
public abstract class AbstractRateLimitFilter extends OncePerRequestFilter {

	private final Cache<String, Bucket> buckets;
	private final long capacity;
	private final long refillTokens;
	private final long refillDurationSeconds;
	private final ObjectMapper objectMapper;

	protected AbstractRateLimitFilter(long capacity, long refillTokens, long refillDurationSeconds,
			ObjectMapper objectMapper) {
		this.capacity = capacity;
		this.refillTokens = refillTokens;
		this.refillDurationSeconds = refillDurationSeconds;
		this.objectMapper = objectMapper;
		this.buckets = Caffeine.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10))
				.maximumSize(100_000)
				.build();
	}

	/** Whether this filter's rate limit applies to the given request — implementations match on a
	 * URI prefix. */
	protected abstract boolean appliesTo(HttpServletRequest request);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!appliesTo(request)) {
			chain.doFilter(request, response);
			return;
		}

		String clientKey = resolveClientKey(request);
		Bucket bucket = buckets.get(clientKey, key -> newBucket());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

		if (probe.isConsumed()) {
			response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
			chain.doFilter(request, response);
			return;
		}

		long waitSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
		response.setStatus(429);
		response.addHeader("Retry-After", String.valueOf(waitSeconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ApiError.of("RATE_LIMITED", "Too many requests, try again in " + waitSeconds + "s"));
	}

	private Bucket newBucket() {
		Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, Duration.ofSeconds(refillDurationSeconds)));
		return Bucket.builder().addLimit(limit).build();
	}

	private String resolveClientKey(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
