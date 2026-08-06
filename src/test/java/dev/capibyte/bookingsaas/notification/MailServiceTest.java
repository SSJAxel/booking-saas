package dev.capibyte.bookingsaas.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailServiceTest {

	private final JavaMailSender javaMailSender = mock(JavaMailSender.class);

	@Test
	void sendsAMessageWithTheConfiguredFromAddressOverSmtpWhenNoResendKeyIsSet() {
		MailService mailService = new MailService(javaMailSender, RestClient.builder(),
				"no-reply@booking-saas.local", "");

		mailService.send("client@example.com", "Subject", "Body");

		verify(javaMailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void aSmtpSendFailureIsSwallowedNotPropagated() {
		MailService mailService = new MailService(javaMailSender, RestClient.builder(),
				"no-reply@booking-saas.local", "");
		doThrow(new MailSendException("smtp unreachable")).when(javaMailSender).send(any(SimpleMailMessage.class));

		assertThatCode(() -> mailService.send("client@example.com", "Subject", "Body")).doesNotThrowAnyException();
	}

	@Test
	void sendsViaResendApiInsteadOfSmtpWhenAnApiKeyIsConfigured() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
		MailService mailService = new MailService(javaMailSender, builder, "no-reply@booking-saas.local", "re_test_key");

		mockServer.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer re_test_key"))
				.andExpect(content().string(containsString("client@example.com")))
				.andRespond(withSuccess("{\"id\":\"abc\"}", org.springframework.http.MediaType.APPLICATION_JSON));

		mailService.send("client@example.com", "Subject", "Body");

		mockServer.verify();
		verifyNoInteractions(javaMailSender);
	}

	@Test
	void aResendSendFailureIsSwallowedNotPropagated() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
		MailService mailService = new MailService(javaMailSender, builder, "no-reply@booking-saas.local", "re_test_key");

		mockServer.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

		assertThatCode(() -> mailService.send("client@example.com", "Subject", "Body")).doesNotThrowAnyException();
	}
}
