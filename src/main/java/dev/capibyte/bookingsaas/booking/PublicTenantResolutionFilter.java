package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.ApiError;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import dev.capibyte.bookingsaas.tenant.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the tenant for the unauthenticated public booking API from the {tenantSlug} path
 * segment (mirrors what JwtAuthenticationFilter does from a JWT claim for the admin API) —
 * so it must run, and fully resolve the tenant, before any controller/service opens a Hibernate
 * session, same reasoning as documented on TenantService/AppUserService.
 */
@Component
@RequiredArgsConstructor
public class PublicTenantResolutionFilter extends OncePerRequestFilter {

	private static final Pattern PUBLIC_PATH = Pattern.compile("^/api/public/([^/]+)(/.*)?$");

	private final TenantService tenantService;
	private final ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Matcher matcher = PUBLIC_PATH.matcher(request.getRequestURI());
		if (!matcher.matches()) {
			chain.doFilter(request, response);
			return;
		}

		String slug = matcher.group(1);
		Optional<Tenant> found = tenantService.findBySlug(slug);
		if (found.isEmpty()) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Unknown business: " + slug);
			return;
		}
		Tenant tenant = found.get();
		if (tenant.getStatus() != TenantStatus.ACTIVE) {
			// Deliberately the same message for PENDING_APPROVAL and SUSPENDED — a client shouldn't
			// be able to tell those apart, and neither should reveal more than "not available yet".
			writeError(response, HttpServletResponse.SC_FORBIDDEN, "TENANT_NOT_ACTIVE",
					"Este negocio todavía no está habilitado");
			return;
		}

		TenantContext.setTenantId(tenant.getId());
		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}

	private void writeError(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiError.of(code, message));
	}
}
