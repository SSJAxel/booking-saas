import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import AdminHelpManual from "../components/AdminHelpManual.jsx";

const LINKS = [
	{ to: "tenants", label: "Cuentas" },
	{ to: "usage", label: "Uso" },
	{ to: "support-reports", label: "Reportes" },
];

function MenuIcon() {
	return (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
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

/** Same topbar + "☰ más opciones" pattern as DashboardLayout (see its comments for the affordance
 * reasoning) — visually consistent with the tenant-facing panel now, even though the nav links
 * themselves are entirely different (platform-wide, not per-tenant operative pages). */
export default function AdminLayout() {
	const { session, logout } = useAuth();
	const [manualOpen, setManualOpen] = useState(false);
	const [menuOpen, setMenuOpen] = useState(false);
	const menuRef = useRef(null);

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

	return (
		<div className="shell shell-top">
			<header className="topbar">
				<div className="topbar-brand">
					<span className="brand-mark" aria-hidden="true">
						A
					</span>
					<span className="brand-name">Admin</span>
				</div>

				<nav className="topbar-nav">
					{LINKS.map((link) => (
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
								<div>
									<div className="topbar-menu-name">{session.email}</div>
									<div className="topbar-menu-role">Super admin</div>
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
								Manual
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
				<Outlet />
			</main>

			<AdminHelpManual open={manualOpen} onClose={() => setManualOpen(false)} />
		</div>
	);
}
