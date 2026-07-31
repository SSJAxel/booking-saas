import { useEffect, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";

export default function TenantPage() {
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");
	const [brandingNotice, setBrandingNotice] = useState("");
	const [timezoneNotice, setTimezoneNotice] = useState("");
	const { session } = useAuth();
	const canManage = session.role === "OWNER" || session.role === "ADMIN";

	async function refresh() {
		try {
			setTenant(await api.tenant.get());
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	async function handleDowngrade() {
		setError("");
		try {
			setTenant(await api.tenant.changePlan("BASIC"));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSubscribe() {
		setError("");
		try {
			const { checkoutUrl } = await api.tenant.subscribe("PRO");
			window.location.href = checkoutUrl;
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleConnectMercadoPago() {
		setError("");
		try {
			const { authorizationUrl } = await api.tenant.connectMercadoPago();
			window.location.href = authorizationUrl;
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSaveBranding(event) {
		event.preventDefault();
		setError("");
		setBrandingNotice("");
		const form = new FormData(event.target);
		try {
			setTenant(
				await api.tenant.updateBranding({
					logoUrl: form.get("logoUrl")?.trim() || null,
					accentColor: form.get("accentColor")?.trim() || null,
					tagline: form.get("tagline")?.trim() || null,
				}),
			);
			setBrandingNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSaveTimezone(event) {
		event.preventDefault();
		event.stopPropagation();
		setError("");
		setTimezoneNotice("");
		const form = new FormData(event.target);
		try {
			setTenant(await api.tenant.updateTimezone(form.get("timezone")));
			setTimezoneNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	if (!tenant) return <p>Cargando...</p>;

	return (
		<div>
			<h1>Negocio</h1>
			{error && <p className="error">{error}</p>}
			<div className="card">
				<p>
					<strong>{tenant.name}</strong> ({tenant.slug})
				</p>
				<p>
					Plan actual: <span className={`badge badge-${tenant.planTier.toLowerCase()}`}>{tenant.planTier}</span>
				</p>
				{session.role === "OWNER" ? (
					<div className="button-row">
						<button type="button" disabled={tenant.planTier === "BASIC"} onClick={handleDowngrade}>
							Pasar a BASIC
						</button>
						<button type="button" disabled={tenant.planTier === "PRO"} onClick={handleSubscribe}>
							Suscribirme a PRO
						</button>
					</div>
				) : (
					<p className="muted">Solo el dueño puede cambiar el plan.</p>
				)}
			</div>

			<p className="label">Zona horaria</p>
			<div className="card">
				<p className="muted">
					Se usa para calcular tus horarios disponibles y a qué hora se registra cada turno — cambiarla no mueve
					los turnos ya cargados.
				</p>
				{canManage ? (
					<form className="inline-form" onSubmit={handleSaveTimezone}>
						<input
							name="timezone"
							list="timezone-options"
							defaultValue={tenant.timezone}
							placeholder="ej: America/Argentina/Buenos_Aires"
						/>
						<datalist id="timezone-options">
							<option value="America/Argentina/Buenos_Aires" />
							<option value="America/Santiago" />
							<option value="America/Montevideo" />
							<option value="America/Sao_Paulo" />
							<option value="America/Bogota" />
							<option value="America/Mexico_City" />
							<option value="America/Lima" />
							<option value="UTC" />
						</datalist>
						<button type="submit">Guardar</button>
						{timezoneNotice && <span className="notice">{timezoneNotice}</span>}
					</form>
				) : (
					<p className="muted">
						Actual: <strong>{tenant.timezone}</strong>. Solo el dueño o un admin pueden cambiarla.
					</p>
				)}
			</div>

			<p className="label">Cobros</p>
			<div className="card">
				<p className="muted">
					Conectá tu propia cuenta de Mercado Pago para que las señas y la suscripción se cobren directo a tu
					cuenta, en vez de una cuenta compartida de la plataforma.
				</p>
				{session.role === "OWNER" ? (
					<button type="button" onClick={handleConnectMercadoPago}>
						Conectar Mercado Pago
					</button>
				) : (
					<p className="muted">Solo el dueño puede conectar la cuenta.</p>
				)}
			</div>

			<p className="label">Marca en tu sitio público</p>
			<div className="card">
				<p className="muted">
					Se ve en tu página pública de reservas ({window.location.protocol}//localhost:5181/{tenant.slug}).
					Dejar un campo vacío lo saca.
				</p>
				{canManage ? (
					<form className="inline-form" onSubmit={handleSaveBranding}>
						<input name="logoUrl" placeholder="URL del logo" defaultValue={tenant.logoUrl ?? ""} />
						<input
							name="accentColor"
							placeholder="#RRGGBB"
							defaultValue={tenant.accentColor ?? ""}
							pattern="#[0-9a-fA-F]{6}"
							title="Formato hexadecimal, ej: #FF5733"
						/>
						<input name="tagline" placeholder="Frase corta (opcional)" defaultValue={tenant.tagline ?? ""} />
						<button type="submit">Guardar</button>
						{brandingNotice && <span className="notice">{brandingNotice}</span>}
					</form>
				) : (
					<p className="muted">Solo el dueño o un admin pueden editar la marca.</p>
				)}
			</div>
		</div>
	);
}
