import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { getStoredTheme, setTheme, systemPrefersDark } from "../theme.js";
import ReportBugModal from "../components/ReportBugModal.jsx";

const ROLE_LABELS = { OWNER: "Dueño/a", ADMIN: "Administrador/a", STAFF: "Staff" };

const LINKS = [
	{ to: "dashboard", label: "Inicio", roles: ["OWNER", "ADMIN"] },
	{ to: "appointments", label: "Turnos" },
	{ to: "branches", label: "Sucursales" },
	{ to: "professionals", label: "Profesionales" },
	{ to: "services", label: "Servicios" },
	{ to: "products", label: "Productos" },
	{ to: "tenant", label: "Mi Plan" },
	{ to: "account", label: "Mi cuenta" },
];

function ReportIcon() {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M12 9v4M12 17h.01M10.29 3.86 1.82 18a1.5 1.5 0 0 0 1.29 2.25h17.78A1.5 1.5 0 0 0 22.18 18L13.71 3.86a1.5 1.5 0 0 0-2.42 0Z"
				stroke="currentColor"
				strokeWidth="1.8"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function LogoutIcon() {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"
				stroke="currentColor"
				strokeWidth="1.8"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

export default function DashboardLayout() {
	const { session, me, logout } = useAuth();
	const [theme, setThemeState] = useState(() => getStoredTheme() ?? (systemPrefersDark() ? "dark" : "light"));
	const [reportBugOpen, setReportBugOpen] = useState(false);
	const links = LINKS.filter((link) => !link.roles || link.roles.includes(session.role));

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
					{links.map((link) => (
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
						<div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.4rem" }}>
							{me?.avatarUrl && <img src={me.avatarUrl} alt="" className="sidebar-avatar" />}
							<span>{me?.displayName || session.email}</span>
						</div>
						{ROLE_LABELS[session.role] ?? session.role}
					</div>
					<div className="button-row">
						<button type="button" className="secondary icon-button" onClick={() => setReportBugOpen(true)}>
							<ReportIcon />
							Reportar un problema
						</button>
						<button type="button" className="danger icon-button" onClick={logout}>
							<LogoutIcon />
							Salir
						</button>
					</div>
				</div>
			</aside>
			<main className="content">
				<Outlet />
			</main>
			<ReportBugModal open={reportBugOpen} onClose={() => setReportBugOpen(false)} />
		</div>
	);
}
