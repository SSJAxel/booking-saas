package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Without this, Spring Security falls back to Http403ForbiddenEntryPoint for any unauthenticated
 * request to a protected path (no httpBasic/formLogin registered to supply a default one) — which
 * conflates "not authenticated" with "authenticated but forbidden". This keeps 401 vs 403 correct
 * and the error body consistent with {@link dev.capibyte.bookingsaas.common.GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiError.of("UNAUTHORIZED", "Authentication required"));
	}
}
