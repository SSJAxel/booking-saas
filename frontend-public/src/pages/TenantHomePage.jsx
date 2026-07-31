import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api.js";

export default function TenantHomePage() {
	const { tenantSlug } = useParams();
	const [tenant, setTenant] = useState(null);
	const [services, setServices] = useState([]);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		setLoading(true);
		setError("");
		Promise.all([api.getTenant(tenantSlug), api.getServices(tenantSlug)])
			.then(([t, s]) => {
				setTenant(t);
				setServices(s);
			})
			.catch((err) => setError(err.message))
			.finally(() => setLoading(false));
	}, [tenantSlug]);

	if (loading) return <p className="status">Cargando...</p>;
	if (error) return <p className="status error">{error}</p>;

	return (
		<div className="page">
			<header className="hero">
				<h1>{tenant.name}</h1>
				<p className="muted">Elegí un servicio para reservar tu turno</p>
			</header>
			<div className="service-grid">
				{services.length === 0 && <p className="muted">Todavía no hay servicios cargados.</p>}
				{services.map((s) => (
					<Link key={s.id} to={`/${tenantSlug}/reservar/${s.id}`} className="service-card">
						<h3>{s.name}</h3>
						{s.description && <p className="muted">{s.description}</p>}
						<div className="service-meta">
							<span>{s.durationMinutes} min</span>
							<span className="price">${s.price}</span>
						</div>
					</Link>
				))}
			</div>
		</div>
	);
}
