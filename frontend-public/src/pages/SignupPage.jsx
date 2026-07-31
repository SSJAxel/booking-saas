import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api.js";

export default function SignupPage() {
	const [searchParams] = useSearchParams();
	const wantsPro = searchParams.get("plan") === "PRO";
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const [created, setCreated] = useState(null);

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

	if (created) {
		return (
			<div className="page">
				<div className="confirmation">
					<h1>¡Listo, {created.tenantSlug} ya existe!</h1>
					<p>
						Tu cuenta arranca en el plan Básico. {wantsPro && "Para pasar a Pro, "}
						{wantsPro
							? "entrá al panel y suscribite desde ahí."
							: "Podés cambiar de plan cuando quieras desde el panel."}
					</p>
					<a className="button-link" href="http://localhost:5180/login">
						Ir al panel de administración
					</a>
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
					? "Arrancás en el plan Básico y pasás a Pro apenas entrás al panel."
					: "Es gratis empezar — podés pasar a Pro cuando quieras."}
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
