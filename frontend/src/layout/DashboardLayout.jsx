import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { getStoredTheme, setTheme, systemPrefersDark } from "../theme.js";
import { planHasProducts, planHasReviews } from "../planLimits.js";
import ReportBugModal from "../components/ReportBugModal.jsx";
import HelpManual from "../components/HelpManual.jsx";

const ROLE_LABELS = { OWNER: "Dueño/a", ADMIN: "Administrador/a", STAFF: "Staff" };

// The main horizontal nav — "Mi Plan" and "Mi cuenta" live in the "⋯ más opciones" menu instead,
// alongside settings/account actions rather than day-to-day operative pages.
const NAV_LINKS = [
	{ to: "dashboard", label: "Inicio", roles: ["OWNER", "ADMIN"] },
	{ to: "appointments", label: "Turnos" },
	{ to: "branches", label: "Sucursales" },
	{ to: "professionals", label: "Profesionales" },
	{ to: "services", label: "Servicios" },
	{ to: "products", label: "Productos", requiresProducts: true },
	{ to: "reviews", label: "Reseñas", roles: ["OWNER", "ADMIN"], requiresReviews: true },
];

function MenuIcon() {
	return (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
		</svg>
	);
}

function PlanIcon() {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M12 2 2 7l10 5 10-5-10-5ZM2 17l10 5 10-5M2 12l10 5 10-5"
				stroke="currentColor"
				strokeWidth="1.8"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function AccountIcon() {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M20 21a8 8 0 0 0-16 0M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"
				stroke="currentColor"
				strokeWidth="1.8"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function ManualIcon() {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path
				d="M4 19.5V6a2 2 0 0 1 2-2h13a1 1 0 0 1 1 1v13.5M6 22a2 2 0 0 1-2-2.5C4.2 18.5 5 18 6 18h14v3"
				stroke="currentColor"
				strokeWidth="1.8"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

function ReportIcon() {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
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
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
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
	const [manualOpen, setManualOpen] = useState(false);
	const [menuOpen, setMenuOpen] = useState(false);
	const [tenant, setTenant] = useState(null);
	const menuRef = useRef(null);
	const links = NAV_LINKS.filter((link) => !link.roles || link.roles.includes(session.role))
		.filter((link) => !link.requiresProducts || !tenant || planHasProducts(tenant.planTier))
		.filter((link) => !link.requiresReviews || !tenant || planHasReviews(tenant.planTier));

	useEffect(() => {
		api.tenant
			.get()
			.then(setTenant)
			.catch(() => {});
	}, []);

	// Closes the "más opciones" menu on an outside click or Escape — same affordance as the modals
	// it opens (HelpManual/ReportBugModal), so it doesn't feel like a stray leftover element.
	useEffect(() => {
		if (!menuOpen) return;
		function onPointerDown(event) {
			if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false);
		}
		function onKeyDown(event) {
			if (event.key === "Escape") setMenuOpen(false);
		}
		document.addEventListener("mousedown", onPointerDown);
		document.addEventListener("keydown", onKeyDown);
		return () => {
			document.removeEventListener("mousedown", onPointerDown);
			document.removeEventListener("keydown", onKeyDown);
		};
	}, [menuOpen]);

	function chooseTheme(next) {
		setTheme(next);
		setThemeState(next);
	}

	return (
		<div className="shell shell-top">
			<header className="topbar">
				<div className="topbar-brand">
					<span className="brand-mark" aria-hidden="true">
						{session.tenantSlug.charAt(0).toUpperCase()}
					</span>
					<span className="brand-name">{session.tenantSlug}</span>
				</div>

				<nav className="topbar-nav">
					{links.map((link) => (
						<NavLink key={link.to} to={link.to} className={({ isActive }) => (isActive ? "active" : "")}>
							{link.label}
						</NavLink>
					))}
				</nav>

				<div className="topbar-actions" ref={menuRef}>
					<button
						type="button"
						className="menu-trigger"
						aria-haspopup="true"
						aria-expanded={menuOpen}
						aria-label="Más opciones"
						onClick={() => setMenuOpen((v) => !v)}
					>
						<MenuIcon />
					</button>

					{menuOpen && (
						<div className="topbar-menu" role="menu">
							<div className="topbar-menu-header">
								{me?.avatarUrl && <img src={me.avatarUrl} alt="" className="sidebar-avatar" />}
								<div>
									<div className="topbar-menu-name">{me?.displayName || session.email}</div>
									<div className="topbar-menu-role">{ROLE_LABELS[session.role] ?? session.role}</div>
								</div>
							</div>

							<NavLink
								to="tenant"
								className={({ isActive }) => `topbar-menu-item${isActive ? " active" : ""}`}
								onClick={() => setMenuOpen(false)}
							>
								<PlanIcon />
								Mi plan
							</NavLink>
							<NavLink
								to="account"
								className={({ isActive }) => `topbar-menu-item${isActive ? " active" : ""}`}
								onClick={() => setMenuOpen(false)}
							>
								<AccountIcon />
								Mi cuenta
							</NavLink>

							<div className="topbar-menu-theme">
								<span>Tema</span>
								<div className="theme-toggle" role="group" aria-label="Tema">
									<button
										type="button"
										className={theme === "light" ? "active" : ""}
										onClick={() => chooseTheme("light")}
									>
										Claro
									</button>
									<button type="button" className={theme === "dark" ? "active" : ""} onClick={() => chooseTheme("dark")}>
										Oscuro
									</button>
								</div>
							</div>

							<button
								type="button"
								className="topbar-menu-item"
								onClick={() => {
									setManualOpen(true);
									setMenuOpen(false);
								}}
							>
								<ManualIcon />
								Manual del panel
							</button>
							<button
								type="button"
								className="topbar-menu-item"
								onClick={() => {
									setReportBugOpen(true);
									setMenuOpen(false);
								}}
							>
								<ReportIcon />
								Reportar un problema
							</button>
							<button type="button" className="topbar-menu-item topbar-menu-danger" onClick={logout}>
								<LogoutIcon />
								Salir
							</button>
						</div>
					)}
				</div>
			</header>

			<main className="content">
				{tenant?.status === "PENDING_APPROVAL" && (
					<div className="pending-approval-banner">
						Tu cuenta está en revisión — vas a poder recibir turnos en tu sitio público en cuanto la
						aprobemos. Mientras tanto podés cargar tranquilo sucursales, profesionales y servicios.
					</div>
				)}
				<Outlet />
			</main>

			<ReportBugModal open={reportBugOpen} onClose={() => setReportBugOpen(false)} />
			<HelpManual open={manualOpen} onClose={() => setManualOpen(false)} />
		</div>
	);
}
