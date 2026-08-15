package dev.capibyte.bookingsaas.identity;

import dev.capibyte.bookingsaas.common.UnauthorizedException;

public class EmailNotVerifiedException extends UnauthorizedException {

	public EmailNotVerifiedException() {
		super("Confirmá tu email antes de iniciar sesión — revisá tu casilla por el link de verificación");
	}
}
