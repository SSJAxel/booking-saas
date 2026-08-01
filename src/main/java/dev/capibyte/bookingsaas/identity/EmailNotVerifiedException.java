package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.UnauthorizedException;

public class EmailNotVerifiedException extends UnauthorizedException {

	public EmailNotVerifiedException() {
		super("Confirm your email before logging in — check your inbox for the verification link");
	}
}
