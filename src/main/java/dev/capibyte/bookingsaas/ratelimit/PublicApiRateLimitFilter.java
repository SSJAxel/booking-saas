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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Token-bucket rate limit on the unauthenticated public booking API, keyed by client IP — there's
 * no user identity to key on there, unlike the JWT-authenticated admin API. One bucket per IP
 * covers every /api/public/** route (not tiered per-endpoint); buckets are cached in Caffeine
 * with an idle expiry so the map of "every IP that ever called us" doesn't grow forever.
 *
 * Caveat: trusts X-Forwarded-For if present, which only makes sense behind a reverse proxy that
 * sets/overwrites that header itself — a directly internet-facing deployment would let a client
 * spoof a new IP per request and bypass this entirely. Fine for this project's scope; a real
 * production deployment needs the proxy to strip/replace client-supplied X-Forwarded-For values.
 */
@Component
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

	private static final String PUBLIC_API_PREFIX = "/api/public/";

	private final Cache<String, Bucket> buckets;
	private final long capacity;
	private final long refillTokens;
	private final long refillDurationSeconds;
	private final ObjectMapper objectMapper;

	public PublicApiRateLimitFilter(
			@Value("${app.rate-limit.capacity}") long capacity,
			@Value("${app.rate-limit.refill-tokens}") long refillTokens,
			@Value("${app.rate-limit.refill-duration-seconds}") long refillDurationSeconds,
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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(PUBLIC_API_PREFIX)) {
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
