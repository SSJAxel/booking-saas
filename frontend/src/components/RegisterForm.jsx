import { useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";

/**
 * Just the form + post-submit "revisá tu mail" state, no page/card wrapper — shared between
 * LoginPage's "Crear negocio" tab and PricingPage's sign-up CTA, so both stay in sync with a
 * single copy of the register/resend logic instead of two.
 */
export default function RegisterForm() {
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const [registered, setRegistered] = useState(null);
	const [resendNotice, setResendNotice] = useState("");
	const { register } = useAuth();

	async function handleRegister(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			const response = await register({
				tenantName: form.get("tenantName"),
				tenantSlug: form.get("tenantSlug"),
				ownerEmail: form.get("ownerEmail"),
				ownerPassword: form.get("ownerPassword"),
			});
			setRegistered(response);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	async function handleResend() {
		setResendNotice("");
		try {
			await api.resendVerification({ tenantSlug: registered.tenantSlug, email: registered.email });
			setResendNotice("Te reenviamos el mail.");
		} catch (err) {
			setError(err.message);
		}
	}

	if (registered) {
		return (
			<>
				<h1>Revisá tu mail</h1>
				<p className="muted">
					Te mandamos un link de confirmación a <strong>{registered.email}</strong>. Confirmalo para poder
					entrar — el negocio ya está creado, solo falta ese paso.
				</p>
				{resendNotice && <p className="notice">{resendNotice}</p>}
				{error && <p className="error">{error}</p>}
				<button type="button" onClick={handleResend}>
					Reenviar mail
				</button>
			</>
		);
	}

	return (
		<form onSubmit={handleRegister}>
			{error && <p className="error">{error}</p>}
			<label>
				Nombre del negocio
				<input name="tenantName" required />
			</label>
			<label>
				Identificador del negocio (minúsculas, sin espacios)
				<input name="tenantSlug" required pattern="[a-z0-9](-?[a-z0-9])*" />
			</label>
			<label>
				Email del dueño
				<input name="ownerEmail" type="email" required />
			</label>
			<label>
				Contraseña (mín. 8 caracteres)
				<input name="ownerPassword" type="password" minLength={8} required />
			</label>
			<button type="submit" disabled={loading}>
				{loading ? "Creando..." : "Crear negocio"}
			</button>
		</form>
	);
}
