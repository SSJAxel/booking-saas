import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";

const LINKS = [
	{ to: "tenants", label: "Cuentas" },
	{ to: "support-reports", label: "Reportes" },
];

/** Separate from DashboardLayout on purpose — the platform-admin view shares nothing operative
 * (Turnos/Sucursales/etc.) with a tenant's own sidebar, so bolting it onto DashboardLayout would
 * mean conditionally hiding half its links instead of just not rendering them. */
export default function AdminLayout() {
	const { session, logout } = useAuth();

	return (
		<div className="shell">
			<aside className="sidebar">
				<div className="brand">
					<span className="brand-mark" aria-hidden="true">
						A
					</span>
					<span className="brand-name">Admin</span>
				</div>
				<nav>
					{LINKS.map((link) => (
						<NavLink key={link.to} to={link.to} className={({ isActive }) => (isActive ? "active" : "")}>
							{link.label}
						</NavLink>
					))}
				</nav>
				<div className="sidebar-footer">
					<div className="role">{session.email}</div>
					<div className="button-row">
						<button type="button" onClick={logout}>
							Salir
						</button>
					</div>
				</div>
			</aside>
			<main className="content">
				<Outlet />
			</main>
		</div>
	);
}
