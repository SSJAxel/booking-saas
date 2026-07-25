package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

	public InvalidCredentialsException() {
		super("Invalid tenant, email or password");
	}
}
