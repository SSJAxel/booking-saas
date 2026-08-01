import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";

/**
 * Shown on the landing page (Turnos) while the tenant has zero branches — the most reliable signal
 * that this is a brand-new, unconfigured account, not just "happens to have no branches today".
 * Dismissible on top of that (per tenant, in localStorage) so a business that deliberately never
 * adds a branch isn't stuck with this forever.
 */
export default function WelcomeBanner({ onDismiss }) {
	const { session } = useAuth();
	const [dismissed, setDismissed] = useState(false);

	function dismiss() {
		localStorage.setItem(`onboarding-dismissed-${session.tenantSlug}`, "true");
		setDismissed(true);
		onDismiss?.();
	}

	if (dismissed) return null;

	return (
		<div className="card welcome-banner">
			<h3>Bienvenido a booking-saas</h3>
			<p>
				Este es el panel de administración de <strong>{session.tenantSlug}</strong> — desde acá configurás tu
				negocio. Tus clientes van a reservar turnos desde tu página pública, no desde este panel; este panel es
				solo para vos y tu equipo.
			</p>
			<p className="label">Para empezar a recibir reservas necesitás cargar, en este orden:</p>
			<ol className="welcome-steps">
				<li>
					Una <Link to="/branches">sucursal</Link> — dónde atendés.
				</li>
				<li>
					Un <Link to="/professionals">profesional</Link> — quién atiende, y su horario semanal.
				</li>
				<li>
					Un <Link to="/services">servicio</Link> — qué ofrecés, y quién de tu equipo lo puede atender.
				</li>
			</ol>
			<button type="button" className="secondary" onClick={dismiss}>
				Entendido, no mostrar más
			</button>
		</div>
	);
}
