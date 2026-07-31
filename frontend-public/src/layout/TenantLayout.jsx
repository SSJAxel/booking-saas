import { useEffect, useState } from "react";
import { Link, Outlet, useParams } from "react-router-dom";
import { api } from "../api.js";

export default function TenantLayout() {
	const { tenantSlug } = useParams();
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		setLoading(true);
		setError("");
		api
			.getTenant(tenantSlug)
			.then(setTenant)
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, [tenantSlug]);

	if (loading) return <p className="status">Cargando...</p>;
	if (error) return <p className="status error">{error}</p>;

	// CSS custom property override, scoped to this tenant's pages only — falls back to
	// index.css's default --accent for a tenant that hasn't set one.
	const brandStyle = tenant.accentColor ? { "--accent": tenant.accentColor } : undefined;

	return (
		<div style={brandStyle}>
			<div className="tenant-topbar">
				<Link to={`/${tenantSlug}`} className="tenant-topbar-brand">
					{tenant.logoUrl && <img src={tenant.logoUrl} alt="" className="tenant-logo" />}
					<span>{tenant.name}</span>
				</Link>
			</div>
			<Outlet context={{ tenant }} />
		</div>
	);
}
