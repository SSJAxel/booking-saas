import { useEffect, useState } from "react";
import { api } from "../api.js";

/** Read-only cross-section of a tenant's own data (sucursales/profesionales/servicios), opened
 * from a row in AdminTenantsPage — lets the super-admin answer support questions without
 * impersonating the tenant owner's own login. Quick actions (aprobar) reuse the same handlers
 * the table row itself uses, so acting from here keeps AdminTenantsPage's state in sync. */
export default function TenantDetailModal({ tenant, onClose, onApprove }) {
	const [detail, setDetail] = useState(null);
	const [error, setError] = useState("");

	useEffect(() => {
		if (!tenant) return;
		setDetail(null);
		setError("");
		api.admin
			.tenantDetail(tenant.tenantId)
			.then(setDetail)
			.catch((err) => setError(err.message));
	}, [tenant]);

	useEffect(() => {
		if (!tenant) return;
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [tenant, onClose]);

	if (!tenant) return null;

	return (
		<div className="modal-backdrop" onClick={onClose}>
			<div className="modal-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
				<div className="modal-header">
					<h2>{tenant.name}</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					{error && <p className="error">{error}</p>}
					{!detail && !error ? (
						<p className="muted">Cargando...</p>
					) : detail ? (
						<>
							<p className="label">Sucursales ({detail.branches.length})</p>
							{detail.branches.length === 0 ? (
								<p className="muted">Sin sucursales cargadas.</p>
							) : (
								<ul>
									{detail.branches.map((b, i) => (
										<li key={i}>
											{b.name}
											{b.address && ` — ${b.address}`}
											{!b.active && <span className="muted"> (inactiva)</span>}
										</li>
									))}
								</ul>
							)}

							<p className="label">Profesionales ({detail.professionals.length})</p>
							{detail.professionals.length === 0 ? (
								<p className="muted">Sin profesionales cargados.</p>
							) : (
								<ul>
									{detail.professionals.map((p, i) => (
										<li key={i}>
											{p.displayName}
											{!p.active && <span className="muted"> (inactivo)</span>}
										</li>
									))}
								</ul>
							)}

							<p className="label">Servicios ({detail.services.length})</p>
							{detail.services.length === 0 ? (
								<p className="muted">Sin servicios cargados.</p>
							) : (
								<ul>
									{detail.services.map((s, i) => (
										<li key={i}>
											{s.name} — ${Number(s.price).toLocaleString("es-AR")}
											{!s.active && <span className="muted"> (inactivo)</span>}
										</li>
									))}
								</ul>
							)}
						</>
					) : null}

					{tenant.status === "PENDING_APPROVAL" && (
						<div className="button-row" style={{ marginTop: "1.2rem" }}>
							<button
								type="button"
								onClick={() => {
									onApprove(tenant.tenantId);
									onClose();
								}}
							>
								Aprobar
							</button>
						</div>
					)}
				</div>
			</div>
		</div>
	);
}
