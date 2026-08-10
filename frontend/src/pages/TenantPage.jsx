import { useEffect, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";
import HelpManual from "../components/HelpManual.jsx";

// TRIAL keeps its internal name (DB column, enum, API contract) — only what the tenant sees on
// screen says "Demo". See TenantService.create's Javadoc for why the value itself stays TRIAL.
function planLabel(tier) {
	return tier === "TRIAL" ? "Demo" : tier;
}

export default function TenantPage() {
	const [tenant, setTenant] = useState(null);
	const [plans, setPlans] = useState([]);
	const [error, setError] = useState("");
	const [brandingNotice, setBrandingNotice] = useState("");
	const [timezoneNotice, setTimezoneNotice] = useState("");
	const [notificationsNotice, setNotificationsNotice] = useState("");
	const [transferAliasNotice, setTransferAliasNotice] = useState("");
	const [clientRankingNotice, setClientRankingNotice] = useState("");
	const [planUpgradeOpen, setPlanUpgradeOpen] = useState(false);
	const [planUpgradeSending, setPlanUpgradeSending] = useState(false);
	const [planUpgradeNotice, setPlanUpgradeNotice] = useState("");
	const [manualOpen, setManualOpen] = useState(false);
	const { session } = useAuth();
	const canManage = session.role === "OWNER" || session.role === "ADMIN";

	async function refresh() {
		try {
			const [t, p] = await Promise.all([api.tenant.get(), api.tenant.plans()]);
			setTenant(t);
			setPlans(p);
		} catch (err) {
			setError(err.message);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	async function handleChangePlan(tier) {
		setError("");
		try {
			setTenant(await api.tenant.changePlan(tier));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSubscribe(tier) {
		setError("");
		try {
			const { checkoutUrl } = await api.tenant.subscribe(tier);
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
					contactEmail: form.get("contactEmail")?.trim() || null,
					whatsappNumber: form.get("whatsappNumber")?.trim() || null,
					transferAlias: tenant.transferAlias,
				}),
			);
			setBrandingNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSaveTransferAlias(event) {
		event.preventDefault();
		setError("");
		setTransferAliasNotice("");
		const form = new FormData(event.target);
		try {
			setTenant(
				await api.tenant.updateBranding({
					logoUrl: tenant.logoUrl,
					accentColor: tenant.accentColor,
					tagline: tenant.tagline,
					contactEmail: tenant.contactEmail,
					whatsappNumber: tenant.whatsappNumber,
					transferAlias: form.get("transferAlias")?.trim() || null,
				}),
			);
			setTransferAliasNotice("Guardado.");
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

	async function handleRequestPlanUpgrade(event) {
		event.preventDefault();
		setError("");
		setPlanUpgradeSending(true);
		const form = new FormData(event.target);
		try {
			await api.support.requestPlanUpgrade(form.get("note")?.trim() || null);
			setPlanUpgradeOpen(false);
			setPlanUpgradeNotice("Listo, le avisamos al equipo — te contactamos a la brevedad.");
		} catch (err) {
			setError(err.message);
		} finally {
			setPlanUpgradeSending(false);
		}
	}

	async function handleSaveClientRanking(event) {
		event.preventDefault();
		setError("");
		setClientRankingNotice("");
		const form = new FormData(event.target);
		try {
			setTenant(
				await api.tenant.updateClientRanking(
					Number(form.get("topClientsThreshold")),
					Number(form.get("topClientsCount")),
				),
			);
			setClientRankingNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleToggleWhatsApp(event) {
		const enabled = event.target.checked;
		setError("");
		setNotificationsNotice("");
		try {
			setTenant(await api.tenant.updateNotifications(enabled));
			setNotificationsNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	if (!tenant) return <p>Cargando...</p>;

	const bookingUrl = `${window.location.protocol}//${window.location.host}/reservar/${tenant.slug}`;
	const whatsappHref = tenant.whatsappNumber
		? `https://wa.me/${tenant.whatsappNumber.replace(/[^0-9]/g, "")}`
		: null;

	return (
		<div>
			<h1>Mi Plan</h1>
			{error && <p className="error">{error}</p>}

			<p className="label">Accesos rápidos</p>
			<div className="card button-row">
				<button type="button" onClick={() => window.open(bookingUrl, "_blank", "noreferrer")}>
					Ir al sitio de agendamiento
				</button>
				<button
					type="button"
					disabled={!tenant.contactEmail}
					onClick={() => window.location.assign(`mailto:${tenant.contactEmail}`)}
				>
					Escribir por mail
				</button>
				<button
					type="button"
					disabled={!whatsappHref}
					onClick={() => window.open(whatsappHref, "_blank", "noreferrer")}
				>
					Abrir WhatsApp
				</button>
				<button type="button" className="secondary" onClick={() => setManualOpen(true)}>
					Manual del panel
				</button>
				{!tenant.contactEmail || !tenant.whatsappNumber ? (
					<span className="muted">Completá el email y/o el WhatsApp más abajo para habilitar esos botones.</span>
				) : null}
			</div>
			<HelpManual open={manualOpen} onClose={() => setManualOpen(false)} />

			<div className="card">
				<p>
					<strong>{tenant.name}</strong> ({tenant.slug})
				</p>
				<div className="card-header" style={{ alignItems: "center" }}>
					<p style={{ margin: 0 }}>
						Plan actual:{" "}
						<span className={`badge badge-${tenant.planTier.toLowerCase()}`}>{planLabel(tenant.planTier)}</span>
					</p>
					{session.role === "OWNER" &&
						(planUpgradeOpen ? (
							<form className="inline-form small" style={{ margin: 0 }} onSubmit={handleRequestPlanUpgrade}>
								<input name="note" placeholder="¿Qué necesitás? (opcional)" style={{ minWidth: "220px" }} />
								<button type="submit" disabled={planUpgradeSending}>
									{planUpgradeSending ? "Enviando..." : "Enviar"}
								</button>
								<button type="button" className="secondary" onClick={() => setPlanUpgradeOpen(false)}>
									Cancelar
								</button>
							</form>
						) : (
							<button type="button" onClick={() => setPlanUpgradeOpen(true)}>
								Mejorar plan
							</button>
						))}
				</div>
				{planUpgradeNotice && <p className="notice">{planUpgradeNotice}</p>}
				<div className="cards">
					{plans.map((plan) => {
						const isCurrent = plan.tier === tenant.planTier;
						const priceLabel =
							plan.monthlyPrice === null
								? "Próximamente"
								: Number(plan.monthlyPrice) === 0
									? "Gratis"
									: `$${Number(plan.monthlyPrice).toLocaleString("es-AR")}/mes`;
						return (
							<div className="card" key={plan.tier}>
								<h3>
									{planLabel(plan.tier)}
									{isCurrent && " (actual)"}
								</h3>
								<p className="muted">{priceLabel}</p>
								<p className="muted">
									{plan.maxProducts ? `Hasta ${plan.maxProducts} productos` : "Productos ilimitados"}
								</p>
								{session.role === "OWNER" ? (
									<button
										type="button"
										disabled={isCurrent || plan.monthlyPrice === null}
										onClick={() =>
											Number(plan.monthlyPrice) === 0
												? handleChangePlan(plan.tier)
												: handleSubscribe(plan.tier)
										}
									>
										{isCurrent
											? "Tu plan actual"
											: plan.monthlyPrice === null
												? "Próximamente"
												: Number(plan.monthlyPrice) === 0
													? `Pasar a ${planLabel(plan.tier)}`
													: `Suscribirme a ${planLabel(plan.tier)}`}
									</button>
								) : (
									<p className="muted">Solo el dueño puede cambiar el plan.</p>
								)}
							</div>
						);
					})}
				</div>
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
				<p className="muted" style={{ marginTop: "1rem" }}>
					Alternativa: si un cliente reserva un servicio con seña, le mostramos este alias para que
					transfiera directo — vos confirmás el turno a mano desde "Turnos" cuando veas el pago.
				</p>
				{canManage ? (
					<form className="inline-form small" onSubmit={handleSaveTransferAlias}>
						<input
							name="transferAlias"
							placeholder="Alias para transferencias (ej: minegocio.mp)"
							defaultValue={tenant.transferAlias ?? ""}
						/>
						<button type="submit">Guardar</button>
						{transferAliasNotice && <span className="notice">{transferAliasNotice}</span>}
					</form>
				) : (
					<p className="muted">
						Alias actual: <strong>{tenant.transferAlias || "sin cargar"}</strong>. Solo el dueño o un admin
						pueden cambiarlo.
					</p>
				)}
			</div>

			<p className="label">Marca en tu sitio público</p>
			<div className="card">
				<p className="muted">
					Se ve en tu página pública de reservas ({window.location.protocol}//{window.location.host}/reservar/
					{tenant.slug}). Dejar un campo vacío lo saca.
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
						<input
							name="contactEmail"
							type="email"
							placeholder="Email de contacto"
							defaultValue={tenant.contactEmail ?? ""}
						/>
						<input
							name="whatsappNumber"
							placeholder="WhatsApp (ej: +54 9 11 1234-5678)"
							defaultValue={tenant.whatsappNumber ?? ""}
						/>
						<button type="submit">Guardar</button>
						{brandingNotice && <span className="notice">{brandingNotice}</span>}
					</form>
				) : (
					<p className="muted">Solo el dueño o un admin pueden editar la marca.</p>
				)}
			</div>

			<p className="label">Notificaciones</p>
			<div className="card">
				<p className="muted">
					El mail se envía siempre que alguien agenda, confirma o cancela un turno — esto es un canal extra, no un
					reemplazo. Requiere que el cliente haya dejado su teléfono al reservar.
				</p>
				{canManage ? (
					<label className="inline-form" style={{ alignItems: "center", gap: "0.5rem" }}>
						<input type="checkbox" checked={tenant.whatsappEnabled} onChange={handleToggleWhatsApp} />
						Avisar también por WhatsApp
						{notificationsNotice && <span className="notice">{notificationsNotice}</span>}
					</label>
				) : (
					<p className="muted">
						WhatsApp está {tenant.whatsappEnabled ? "activado" : "desactivado"}. Solo el dueño o un admin pueden
						cambiarlo.
					</p>
				)}
			</div>

			<p className="label">Ranking de clientes</p>
			<div className="card">
				<p className="muted">
					Un cliente suma 1 punto por cada turno marcado como completado, y pierde puntos si cancela seguido o
					falta sin avisar (ver "Clientes" en Turnos → Lista). Acá definís cuándo un cliente entra al panel de
					"mejores clientes" y cuántos se muestran como máximo.
				</p>
				{canManage ? (
					<form className="inline-form small" onSubmit={handleSaveClientRanking}>
						<label>
							Calificación mínima
							<input
								name="topClientsThreshold"
								type="number"
								min="0"
								defaultValue={tenant.topClientsThreshold}
								style={{ width: "5rem" }}
							/>
						</label>
						<label>
							Máximo a mostrar (1–15)
							<input
								name="topClientsCount"
								type="number"
								min="1"
								max="15"
								defaultValue={tenant.topClientsCount}
								style={{ width: "5rem" }}
							/>
						</label>
						<button type="submit">Guardar</button>
						{clientRankingNotice && <span className="notice">{clientRankingNotice}</span>}
					</form>
				) : (
					<p className="muted">
						Calificación mínima: <strong>{tenant.topClientsThreshold}</strong>, hasta{" "}
						<strong>{tenant.topClientsCount}</strong> clientes. Solo el dueño o un admin pueden cambiarlo.
					</p>
				)}
			</div>
		</div>
	);
}
