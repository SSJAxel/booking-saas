import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";

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

	return (
		<div className="shell">
			<aside className="sidebar">
				<div className="brand">{session.tenantSlug}</div>
				<nav>
					{LINKS.map((link) => (
						<NavLink key={link.to} to={link.to} className={({ isActive }) => (isActive ? "active" : "")}>
							{link.label}
						</NavLink>
					))}
				</nav>
				<div className="sidebar-footer">
					<div className="role">
						{session.email} · {session.role}
					</div>
					<button type="button" onClick={logout}>
						Salir
					</button>
				</div>
			</aside>
			<main className="content">
				<Outlet />
			</main>
		</div>
	);
}
