import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api.js";

export default function SignupPage() {
	const [searchParams] = useSearchParams();
	const wantsPro = searchParams.get("plan") === "PRO";
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const [created, setCreated] = useState(null);
	const [resendNotice, setResendNotice] = useState("");

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			const response = await api.register({
				tenantName: form.get("tenantName"),
				tenantSlug: form.get("tenantSlug"),
				ownerEmail: form.get("ownerEmail"),
				ownerPassword: form.get("ownerPassword"),
			});
			setCreated(response);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	async function handleResend() {
		setResendNotice("");
		try {
			await api.resendVerification({ tenantSlug: created.tenantSlug, email: created.email });
			setResendNotice("Te reenviamos el mail.");
		} catch (err) {
			setError(err.message);
		}
	}

	if (created) {
		return (
			<div className="page">
				<div className="confirmation">
					<h1>¡Ya casi, {created.tenantSlug}!</h1>
					<p>
						Te mandamos un mail a <strong>{created.email}</strong> para confirmar tu cuenta — confirmalo para
						poder entrar al panel. Arranca en el plan Prueba, con todo activado
						{wantsPro ? "; para pasar a Pro, entrá al panel y suscribite desde ahí." : "."}
					</p>
					{resendNotice && <p className="notice">{resendNotice}</p>}
					{error && <p className="error">{error}</p>}
					<button type="button" className="button-link" onClick={handleResend}>
						Reenviar mail
					</button>
				</div>
			</div>
		);
	}

	return (
		<div className="page">
			<Link to="/" className="back-link">
				&larr; Volver
			</Link>
			<h1>Creá tu negocio</h1>
			<p className="muted">
				{wantsPro
					? "Arrancás en el plan Prueba y pasás a Pro apenas entrás al panel."
					: "Es gratis empezar — podés cambiar de plan cuando quieras."}
			</p>
			{error && <p className="error">{error}</p>}
			<form className="details-form" onSubmit={handleSubmit}>
				<input name="tenantName" placeholder="Nombre del negocio" required />
				<input
					name="tenantSlug"
					placeholder="URL (ej: mi-negocio)"
					required
					pattern="[a-z0-9](-?[a-z0-9])*"
					title="Minúsculas, números y guiones simples"
				/>
				<input name="ownerEmail" type="email" placeholder="Tu email" required />
				<input name="ownerPassword" type="password" placeholder="Contraseña (mín. 8 caracteres)" minLength={8} required />
				<button type="submit" disabled={loading}>
					{loading ? "Creando..." : "Crear mi negocio"}
				</button>
			</form>
		</div>
	);
}
