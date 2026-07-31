import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function ServicesPage() {
	const [services, setServices] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [assignments, setAssignments] = useState({});
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	async function refresh() {
		setLoading(true);
		try {
			const [s, p] = await Promise.all([api.services.list(), api.professionals.list()]);
			setServices(s);
			setProfessionals(p);
			const entries = await Promise.all(s.map(async (svc) => [svc.id, await api.services.listProfessionals(svc.id)]));
			setAssignments(Object.fromEntries(entries));
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	useEffect(() => {
		refresh();
	}, []);

	async function handleCreate(event) {
		event.preventDefault();
		setError("");
		const form = new FormData(event.target);
		const deposit = form.get("depositAmount");
		try {
			await api.services.create({
				name: form.get("name"),
				description: form.get("description") || null,
				durationMinutes: Number(form.get("durationMinutes")),
				price: Number(form.get("price")),
				depositAmount: deposit ? Number(deposit) : null,
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function toggleAssignment(serviceId, professionalId, assigned) {
		setError("");
		try {
			if (assigned) {
				await api.services.unassignProfessional(serviceId, professionalId);
			} else {
				await api.services.assignProfessional(serviceId, professionalId);
			}
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	if (loading) return <p>Cargando...</p>;

	return (
		<div>
			<h1>Servicios</h1>
			{error && <p className="error">{error}</p>}
			<form className="inline-form" onSubmit={handleCreate}>
				<input name="name" placeholder="Nombre" required />
				<input name="description" placeholder="Descripción" />
				<input name="durationMinutes" type="number" min="1" placeholder="Duración (min)" required />
				<input name="price" type="number" step="0.01" min="0" placeholder="Precio" required />
				<input name="depositAmount" type="number" step="0.01" min="0" placeholder="Seña (opcional)" />
				<button type="submit">Agregar</button>
			</form>

			<div className="cards">
				{services.map((s) => (
					<div className="card" key={s.id}>
						<h3>{s.name}</h3>
						<p className="muted">
							{s.durationMinutes} min · ${s.price}
							{s.depositAmount ? ` · seña $${s.depositAmount}` : " · sin seña (confirma solo)"}
						</p>
						<p className="label">Profesionales que lo ofrecen</p>
						<div className="chip-row">
							{professionals.length === 0 && <span className="muted">Cargá un profesional primero.</span>}
							{professionals.map((p) => {
								const assigned = (assignments[s.id] ?? []).includes(p.id);
								return (
									<button
										key={p.id}
										type="button"
										className={`chip ${assigned ? "chip-on" : ""}`}
										onClick={() => toggleAssignment(s.id, p.id, assigned)}
									>
										{p.displayName}
									</button>
								);
							})}
						</div>
					</div>
				))}
			</div>
		</div>
	);
}
