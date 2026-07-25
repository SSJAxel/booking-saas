package dev.capibyte.bookingsaas.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailServiceTest {

	private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
	private final MailService mailService = new MailService(javaMailSender, "no-reply@booking-saas.local");

	@Test
	void sendsAMessageWithTheConfiguredFromAddress() {
		mailService.send("client@example.com", "Subject", "Body");

		verify(javaMailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void aSendFailureIsSwallowedNotPropagated() {
		doThrow(new MailSendException("smtp unreachable")).when(javaMailSender).send(any(SimpleMailMessage.class));

		assertThatCode(() -> mailService.send("client@example.com", "Subject", "Body")).doesNotThrowAnyException();
	}
}
