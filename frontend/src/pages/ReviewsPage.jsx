import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import { planHasReviews } from "../planLimits.js";

export default function ReviewsPage() {
	const [tenant, setTenant] = useState(null);
	const [reviews, setReviews] = useState([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");

	async function refresh() {
		setLoading(true);
		try {
			const [t, r] = await Promise.all([api.tenant.get(), api.reviews.list()]);
			setTenant(t);
			setReviews(r);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	async function handleToggleEnabled(event) {
		const enabled = event.target.checked;
		setError("");
		setNotice("");
		try {
			setTenant(await api.tenant.updateReviews(enabled));
			setNotice("Guardado.");
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleToggleVisibility(id, visible) {
		setError("");
		try {
			const updated = await api.reviews.setVisibility(id, visible);
			setReviews((prev) => prev.map((r) => (r.id === id ? updated : r)));
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDelete(id) {
		if (!window.confirm("¿Eliminar esta reseña? No se puede deshacer.")) return;
		setError("");
		try {
			await api.reviews.delete(id);
			setReviews((prev) => prev.filter((r) => r.id !== id));
		} catch (err) {
			setError(err.message);
		}
	}

	if (loading) return <p>Cargando...</p>;

	if (tenant && !planHasReviews(tenant.planTier)) {
		return (
			<div>
				<h1>Reseñas</h1>
				<p className="muted">
					Tu plan {planLabel(tenant.planTier)} no incluye reseñas públicas de clientes. Mejorá tu plan
					desde "Mi Plan" para habilitar esta sección.
				</p>
			</div>
		);
	}

	return (
		<div>
			<h1>Reseñas</h1>
			{error && <p className="error">{error}</p>}

			<div className="card" style={{ marginBottom: "1rem" }}>
				<label className="inline-form" style={{ alignItems: "center", gap: "0.5rem" }}>
					<input type="checkbox" checked={tenant?.reviewsEnabled ?? false} onChange={handleToggleEnabled} />
					Activar reseñas públicas
					{notice && <span className="notice">{notice}</span>}
				</label>
				<p className="muted" style={{ marginTop: "0.4rem" }}>
					Cuando un turno se marca como completado, el cliente recibe un mail invitándolo a dejar una
					reseña. Se publica al instante en tu página de reservas — podés ocultarla (se puede volver a
					mostrar) o eliminarla directamente (no se puede deshacer) desde acá si hace falta.
				</p>
			</div>

			{reviews.length === 0 ? (
				<p className="muted">Todavía no hay reseñas.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Cliente</th>
							<th>Profesional</th>
							<th>Calificación</th>
							<th>Comentario</th>
							<th>Estado</th>
							<th>Acciones</th>
						</tr>
					</thead>
					<tbody>
						{reviews.map((r) => (
							<tr key={r.id}>
								<td>{r.clientName}</td>
								<td>{r.professionalName}</td>
								<td>{"★".repeat(r.rating)}{"☆".repeat(5 - r.rating)}</td>
								<td>{r.comment || "—"}</td>
								<td>{r.visible ? "Visible" : "Oculta"}</td>
								<td className="button-row">
									<button
										type="button"
										className="secondary"
										onClick={() => handleToggleVisibility(r.id, !r.visible)}
									>
										{r.visible ? "Ocultar" : "Mostrar"}
									</button>
									<button type="button" className="danger" onClick={() => handleDelete(r.id)}>
										Eliminar
									</button>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
