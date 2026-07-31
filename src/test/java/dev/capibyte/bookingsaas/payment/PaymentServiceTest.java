package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentService;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.common.UnauthorizedException;
import dev.capibyte.bookingsaas.payment.dto.CheckoutResponse;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPayment;
import dev.capibyte.bookingsaas.payment.dto.MercadoPagoPreference;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

	private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
	private final AppointmentService appointmentService = mock(AppointmentService.class);
	private final ServiceOfferingService serviceOfferingService = mock(ServiceOfferingService.class);
	private final MercadoPagoClient mercadoPagoClient = mock(MercadoPagoClient.class);
	private final MercadoPagoAccountService mercadoPagoAccountService = mock(MercadoPagoAccountService.class);
	private final WebhookSignatureVerifier signatureVerifier = mock(WebhookSignatureVerifier.class);
	private final PaymentService paymentService = new PaymentService(paymentRepository, appointmentService,
			serviceOfferingService, mercadoPagoClient, mercadoPagoAccountService, signatureVerifier,
			"test-webhook-secret");

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void createCheckoutRejectsAnAppointmentThatDoesNotNeedADeposit() {
		UUID appointmentId = UUID.randomUUID();
		Appointment appointment = new Appointment();
		appointment.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
		when(appointmentService.findById(appointmentId)).thenReturn(appointment);

		assertThatThrownBy(() -> paymentService.createCheckout(appointmentId)).isInstanceOf(BadRequestException.class);
	}

	@Test
	void createCheckoutBuildsAPreferenceForAPendingDeposit() {
		UUID appointmentId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		Appointment appointment = new Appointment();
		appointment.setPaymentStatus(PaymentStatus.PENDING);
		appointment.setServiceId(serviceId);
		when(appointmentService.findById(appointmentId)).thenReturn(appointment);

		ServiceOffering service = new ServiceOffering();
		service.setName("Small Tattoo");
		service.setDepositAmount(new BigDecimal("20.00"));
		when(serviceOfferingService.findById(serviceId)).thenReturn(service);

		// Payment.id has no production setter (Hibernate-assigned only) — save() is mocked, so
		// nothing actually calls Hibernate's UUID generator here. Simulate what it would do.
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
			Payment saved = invocation.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});
		when(mercadoPagoAccountService.resolveAccessToken(any())).thenReturn("tenant-access-token");
		when(mercadoPagoClient.createPreference(eq("tenant-access-token"), any(), any(), anyString(),
				eq(new BigDecimal("20.00"))))
				.thenReturn(new MercadoPagoPreference("pref-1", "https://mp.example/checkout/pref-1"));

		TenantContext.setTenantId(UUID.randomUUID());
		CheckoutResponse response = paymentService.createCheckout(appointmentId);

		assertThat(response.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-1");
		assertThat(response.paymentId()).isNotNull();
	}

	@Test
	void handleWebhookRejectsAnInvalidSignature() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(false);

		assertThatThrownBy(() -> paymentService.handleWebhook("mp-1", "req-1", "bad-signature"))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void handleWebhookConfirmsTheDepositAndAppointmentWhenApproved() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(true);

		UUID tenantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID appointmentId = UUID.randomUUID();
		when(mercadoPagoAccountService.getPlatformAccessToken()).thenReturn("platform-access-token");
		when(mercadoPagoClient.getPayment("platform-access-token", "mp-1"))
				.thenReturn(new MercadoPagoPayment("mp-1", "approved", tenantId + ":" + paymentId));

		Payment payment = new Payment();
		payment.setAppointmentId(appointmentId);
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		paymentService.handleWebhook("mp-1", "req-1", "valid-signature");

		verify(appointmentService).markDepositPaid(appointmentId);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(payment.getProviderPaymentId()).isEqualTo("mp-1");
	}

	@Test
	void handleWebhookDoesNotConfirmTheAppointmentWhenStillPending() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(true);

		UUID tenantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		when(mercadoPagoAccountService.getPlatformAccessToken()).thenReturn("platform-access-token");
		when(mercadoPagoClient.getPayment("platform-access-token", "mp-1"))
				.thenReturn(new MercadoPagoPayment("mp-1", "in_process", tenantId + ":" + paymentId));

		Payment payment = new Payment();
		payment.setAppointmentId(UUID.randomUUID());
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		paymentService.handleWebhook("mp-1", "req-1", "valid-signature");

		verify(appointmentService, never()).markDepositPaid(any());
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
	}

	@Test
	void handleWebhookClearsTenantContextEvenOnFailure() {
		when(signatureVerifier.isValid(anyString(), anyString(), anyString(), eq("test-webhook-secret")))
				.thenReturn(true);
		when(mercadoPagoAccountService.getPlatformAccessToken()).thenReturn("platform-access-token");
		when(mercadoPagoClient.getPayment("platform-access-token", "mp-1"))
				.thenReturn(new MercadoPagoPayment("mp-1", "approved", UUID.randomUUID() + ":" + UUID.randomUUID()));
		when(paymentRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> paymentService.handleWebhook("mp-1", "req-1", "valid-signature"))
				.isInstanceOf(NotFoundException.class);

		assertThat(TenantContext.getTenantId()).isNull();
	}
}
