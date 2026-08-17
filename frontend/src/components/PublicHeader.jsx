import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import "./PublicHeader.css";

/**
 * Shared header for CapiBooking's own public pages (home/pricing, legal, support, FAQ, manual,
 * sitemap) — navigation among those only. Deliberately no link into any tenant's booking page
 * (/reservar/:tenantSlug): those are per-business and not part of site navigation, only ever
 * reached via the link a business shares directly. The call to action adapts to whether someone's
 * already logged in — an owner with a session goes straight back to their panel instead of being
 * sent through /login again every time they land on the public site.
 */
export default function PublicHeader() {
	const [open, setOpen] = useState(false);
	const { session } = useAuth();

	const ctaTo = session ? (session.platformAdmin ? "/admin" : "/panel") : "/login";
	const ctaLabel = session ? "Ir a mi panel →" : "Ingresar →";

	return (
		<header className="pub-header">
			<Link to="/" className="pub-header-logo" onClick={() => setOpen(false)}>
				<img src="/favicon.svg" alt="" />
				Capi<span className="pub-header-accent">Booking</span>
			</Link>

			<button
				type="button"
				className="pub-header-toggle"
				aria-expanded={open}
				aria-controls="pub-header-nav"
				aria-label={open ? "Cerrar menú" : "Abrir menú"}
				onClick={() => setOpen((v) => !v)}
			>
				<span className="pub-header-toggle-bar" />
				<span className="pub-header-toggle-bar" />
				<span className="pub-header-toggle-bar" />
			</button>

			<nav
				id="pub-header-nav"
				className={`pub-header-nav${open ? " pub-header-nav-open" : ""}`}
				aria-label="Menú principal"
			>
				<ul>
					<li>
						<Link to="/" onClick={() => setOpen(false)}>
							Precios
						</Link>
					</li>
					<li>
						<Link to="/soporte" onClick={() => setOpen(false)}>
							Soporte
						</Link>
					</li>
					<li>
						<Link to="/preguntas-frecuentes" onClick={() => setOpen(false)}>
							Ayuda
						</Link>
					</li>
					<li>
						<Link to="/manual-de-uso" onClick={() => setOpen(false)}>
							Manual de uso
						</Link>
					</li>
					<li>
						<Link to={ctaTo} className="pub-header-cta" onClick={() => setOpen(false)}>
							{ctaLabel}
						</Link>
					</li>
				</ul>
			</nav>
		</header>
	);
}
