import { useEffect, useState } from "react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import { api } from "../api.js";

export default function TenantHomePage() {
	const { tenantSlug } = useParams();
	const { tenant } = useOutletContext();
	const [branches, setBranches] = useState([]);
	const [branchesLoading, setBranchesLoading] = useState(true);
	const [selectedBranchId, setSelectedBranchId] = useState(null);
	const [services, setServices] = useState([]);
	const [servicesLoading, setServicesLoading] = useState(false);
	const [error, setError] = useState("");

	const needsBranchChoice = branches.length > 1;
	const readyForServices = !branchesLoading && (!needsBranchChoice || selectedBranchId);

	useEffect(() => {
		setBranchesLoading(true);
		setError("");
		api
			.getBranches(tenantSlug)
			.then((list) => {
				setBranches(list);
				// A single-branch tenant (still the common case) never has to choose — same
				// experience as before this feature existed.
				if (list.length <= 1) setSelectedBranchId(list[0]?.id ?? null);
			})
			.catch((err) => setError(err.message))
			.finally(() => setBranchesLoading(false));
	}, [tenantSlug]);

	useEffect(() => {
		if (!readyForServices) return;
		setServicesLoading(true);
		api
			.getServices(tenantSlug, selectedBranchId ?? undefined)
			.then(setServices)
			.catch((err) => setError(err.message))
			.finally(() => setServicesLoading(false));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [tenantSlug, selectedBranchId, readyForServices]);

	if (branchesLoading) return <p className="status">Cargando...</p>;
	if (error) return <p className="status error">{error}</p>;

	return (
		<div className="page">
			<header className="hero">
				<h1>{tenant.name}</h1>
				<p className="muted">{tenant.tagline || "Elegí un servicio para reservar tu turno"}</p>
			</header>

			{needsBranchChoice && (
				<div className="branch-picker">
					<p className="label">Elegí una sucursal</p>
					<div className="chip-row">
						{branches.map((b) => (
							<button
								key={b.id}
								type="button"
								className={`chip ${selectedBranchId === b.id ? "chip-on" : ""}`}
								onClick={() => setSelectedBranchId(b.id)}
							>
								{b.name}
							</button>
						))}
					</div>
				</div>
			)}

			{readyForServices &&
				(servicesLoading ? (
					<p className="status">Cargando servicios...</p>
				) : (
					<div className="service-grid">
						{services.length === 0 && <p className="muted">Todavía no hay servicios cargados en esta sucursal.</p>}
						{services.map((s) => (
							<Link
								key={s.id}
								to={`/${tenantSlug}/reservar/${s.id}${needsBranchChoice ? `?branch=${selectedBranchId}` : ""}`}
								className="service-card"
							>
								<h3>{s.name}</h3>
								{s.description && <p className="muted">{s.description}</p>}
								<div className="service-meta">
									<span>{s.durationMinutes} min</span>
									<span className="price">${s.price}</span>
								</div>
							</Link>
						))}
					</div>
				))}
		</div>
	);
}
