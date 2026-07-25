package dev.capibyte.bookingsaas.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

	private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier();

	@Test
	void acceptsACorrectlyComputedSignature() {
		String secret = "test-webhook-secret";
		String dataId = "123456";
		String requestId = "req-abc";
		String ts = "1700000000";
		String header = "ts=" + ts + ",v1=" + hmac(manifest(dataId, requestId, ts), secret);

		assertThat(verifier.isValid(dataId, requestId, header, secret)).isTrue();
	}

	@Test
	void rejectsASignatureComputedWithTheWrongSecret() {
		String dataId = "123456";
		String requestId = "req-abc";
		String ts = "1700000000";
		String header = "ts=" + ts + ",v1=" + hmac(manifest(dataId, requestId, ts), "a-different-secret");

		assertThat(verifier.isValid(dataId, requestId, header, "test-webhook-secret")).isFalse();
	}

	@Test
	void rejectsATamperedDataId() {
		String secret = "test-webhook-secret";
		String requestId = "req-abc";
		String ts = "1700000000";
		String header = "ts=" + ts + ",v1=" + hmac(manifest("123456", requestId, ts), secret);

		// signature was computed for a different data.id than the one presented
		assertThat(verifier.isValid("999999", requestId, header, secret)).isFalse();
	}

	@Test
	void rejectsWhenNoSecretIsConfigured() {
		assertThat(verifier.isValid("123", "req", "ts=1,v1=whatever", null)).isFalse();
		assertThat(verifier.isValid("123", "req", "ts=1,v1=whatever", "")).isFalse();
	}

	@Test
	void rejectsAMissingOrMalformedSignatureHeader() {
		assertThat(verifier.isValid("123", "req", null, "secret")).isFalse();
		assertThat(verifier.isValid("123", "req", "not-a-valid-header", "secret")).isFalse();
	}

	private String manifest(String dataId, String requestId, String ts) {
		return "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
	}

	private String hmac(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
