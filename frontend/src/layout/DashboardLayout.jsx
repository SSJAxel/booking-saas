import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { getStoredTheme, setTheme, systemPrefersDark } from "../theme.js";
import HelpManual from "../components/HelpManual.jsx";

const LINKS = [
	{ to: "appointments", label: "Turnos" },
	{ to: "branches", label: "Sucursales" },
	{ to: "professionals", label: "Profesionales" },
	{ to: "services", label: "Servicios" },
	{ to: "products", label: "Productos" },
	{ to: "tenant", label: "Plan" },
];

export default function DashboardLayout() {
	const { session, logout } = useAuth();
	const [theme, setThemeState] = useState(() => getStoredTheme() ?? (systemPrefersDark() ? "dark" : "light"));
	const [manualOpen, setManualOpen] = useState(false);

	function chooseTheme(next) {
		setTheme(next);
		setThemeState(next);
	}

	return (
		<div className="shell">
			<aside className="sidebar">
				<div className="brand">
					<span className="brand-mark" aria-hidden="true">
						{session.tenantSlug.charAt(0).toUpperCase()}
					</span>
					<span className="brand-name">{session.tenantSlug}</span>
				</div>
				<nav>
					{LINKS.map((link) => (
						<NavLink key={link.to} to={link.to} className={({ isActive }) => (isActive ? "active" : "")}>
							{link.label}
						</NavLink>
					))}
				</nav>
				<div className="theme-toggle" role="group" aria-label="Tema">
					<button type="button" className={theme === "light" ? "active" : ""} onClick={() => chooseTheme("light")}>
						Claro
					</button>
					<button type="button" className={theme === "dark" ? "active" : ""} onClick={() => chooseTheme("dark")}>
						Oscuro
					</button>
				</div>
				<div className="sidebar-footer">
					<div className="role">
						{session.email} · {session.role}
					</div>
					<div className="button-row">
						<button type="button" className="secondary" onClick={() => setManualOpen(true)}>
							? Manual
						</button>
						<button type="button" onClick={logout}>
							Salir
						</button>
					</div>
				</div>
			</aside>
			<main className="content">
				<Outlet />
			</main>
			<HelpManual open={manualOpen} onClose={() => setManualOpen(false)} />
		</div>
	);
}
