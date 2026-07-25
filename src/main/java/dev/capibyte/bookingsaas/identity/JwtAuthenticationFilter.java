package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT auth: the token itself carries identity/role, no DB round-trip per request.
 * Also the sole place {@link TenantContext} gets populated for the authenticated admin/staff
 * API (the public booking API resolves tenant from the URL slug instead — see BookingController).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			try {
				Claims claims = jwtService.parseClaims(header.substring(7));
				UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
				UUID userId = UUID.fromString(claims.getSubject());
				String role = claims.get("role", String.class);

				TenantContext.setTenantId(tenantId);

				var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
				var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException e) {
				log.debug("Rejecting invalid JWT: {}", e.getMessage());
				SecurityContextHolder.clearContext();
			}
		}
		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}
}
