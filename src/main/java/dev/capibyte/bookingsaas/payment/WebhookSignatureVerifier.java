package dev.capibyte.bookingsaas.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Validates MercadoPago's webhook signature per their documented scheme: the {@code x-signature}
 * header is {@code ts=<epoch>,v1=<hex hmac>}, and the HMAC-SHA256 is computed over the manifest
 * string {@code id:<dataId>;request-id:<x-request-id>;ts:<ts>;} using the integration's webhook
 * secret. Not exercised against a real MercadoPago webhook call (no sandbox credentials
 * available) — implemented strictly from their published spec, so treat as needing a live check
 * before depending on it in production.
 */
@Component
public class WebhookSignatureVerifier {

	public boolean isValid(String dataId, String xRequestId, String xSignatureHeader, String secret) {
		if (secret == null || secret.isBlank()) {
			// No secret configured (e.g. local dev without a real MercadoPago integration set up)
			// — can't verify, so don't pretend to. Caller decides whether that's acceptable.
			return false;
		}
		if (xSignatureHeader == null || xSignatureHeader.isBlank()) {
			return false;
		}

		Map<String, String> parts = parseSignatureHeader(xSignatureHeader);
		String ts = parts.get("ts");
		String expectedHash = parts.get("v1");
		if (ts == null || expectedHash == null) {
			return false;
		}

		String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
		String actualHash = hmacSha256Hex(manifest, secret);
		return actualHash.equalsIgnoreCase(expectedHash);
	}

	private Map<String, String> parseSignatureHeader(String header) {
		return java.util.Arrays.stream(header.split(","))
				.map(String::trim)
				.filter(part -> part.contains("="))
				.map(part -> part.split("=", 2))
				.collect(java.util.stream.Collectors.toMap(kv -> kv[0].trim(), kv -> kv[1].trim()));
	}

	private String hmacSha256Hex(String data, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Failed to compute webhook signature", e);
		}
	}
}
