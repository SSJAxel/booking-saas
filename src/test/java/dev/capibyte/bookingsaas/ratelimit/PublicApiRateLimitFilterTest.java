package dev.capibyte.bookingsaas.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class PublicApiRateLimitFilterTest {

	private final PublicApiRateLimitFilter filter =
			new PublicApiRateLimitFilter(3, 3, 60, JsonMapper.builder().build());

	@Test
	void allowsRequestsUnderTheCapacityAndBlocksTheNextOne() throws Exception {
		FilterChain chain = mock(FilterChain.class);

		for (int i = 0; i < 3; i++) {
			MockHttpServletRequest request = publicRequest("1.2.3.4");
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, chain);
			assertThat(response.getStatus()).isEqualTo(200);
		}
		verify(chain, times(3)).doFilter(any(), any());

		MockHttpServletRequest fourth = publicRequest("1.2.3.4");
		MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
		filter.doFilter(fourth, fourthResponse, chain);

		assertThat(fourthResponse.getStatus()).isEqualTo(429);
		assertThat(fourthResponse.getHeader("Retry-After")).isNotNull();
		assertThat(fourthResponse.getContentAsString()).contains("RATE_LIMITED");
		verify(chain, times(3)).doFilter(any(), any()); // still 3 — the 4th never reached the chain
	}

	@Test
	void differentClientIpsGetIndependentBuckets() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		for (int i = 0; i < 3; i++) {
			filter.doFilter(publicRequest("1.1.1.1"), new MockHttpServletResponse(), chain);
		}

		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(publicRequest("2.2.2.2"), response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void requestsOutsideThePublicApiAreNeverRateLimited() throws Exception {
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/branches");
		request.setRemoteAddr("1.2.3.4");

		for (int i = 0; i < 10; i++) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, chain);
			assertThat(response.getStatus()).isEqualTo(200);
		}
	}

	private MockHttpServletRequest publicRequest(String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/tattoo-ink/services");
		request.setRemoteAddr(remoteAddr);
		return request;
	}
}
