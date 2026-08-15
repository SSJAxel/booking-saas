import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { StoreIcon, MailIcon } from "../components/icons.jsx";

/**
 * Always shows the same generic success message once the request completes, regardless of whether
 * the tenant/email combination actually exists — the backend (AuthService.forgotPassword) is
 * deliberately enumeration-safe (always 204), and this page must not undo that by reacting
 * differently to "found" vs "not found".
 */
export default function ForgotPasswordPage() {
	const { forgotPassword } = useAuth();
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const [sent, setSent] = useState(false);

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			await forgotPassword({ tenantSlug: form.get("tenantSlug"), email: form.get("email") });
			setSent(true);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="auth-page">
			<div className="auth-card">
				<h1>Recuperar contraseña</h1>
				{sent ? (
					<p className="muted">
						Si existe una cuenta con esos datos, te enviamos un mail con instrucciones para restablecer tu
						contraseña. Revisá también la carpeta de spam.
					</p>
				) : (
					<form onSubmit={handleSubmit}>
						{error && <p className="error">{error}</p>}
						<label>
							Identificador del negocio
							<span className="input-icon-wrap">
								<StoreIcon width="16" height="16" />
								<input name="tenantSlug" required />
							</span>
						</label>
						<label>
							Email
							<span className="input-icon-wrap">
								<MailIcon width="16" height="16" />
								<input name="email" type="email" required />
							</span>
						</label>
						<button type="submit" disabled={loading}>
							{loading ? "Enviando..." : "Enviar instrucciones"}
						</button>
					</form>
				)}
				<p className="muted" style={{ marginTop: "1rem" }}>
					<Link to="/login">Volver a ingresar</Link>
				</p>
			</div>
		</div>
	);
}
