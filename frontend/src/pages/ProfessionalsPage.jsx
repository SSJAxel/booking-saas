import { useEffect, useState } from "react";
import { api } from "../api.js";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export default function ProfessionalsPage() {
	const [branches, setBranches] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);

	async function refresh() {
		setLoading(true);
		try {
			const [b, p] = await Promise.all([api.branches.list(), api.professionals.list()]);
			setBranches(b);
			setProfessionals(p);
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
		try {
			await api.professionals.create({
				branchId: form.get("branchId"),
				displayName: form.get("displayName"),
				bio: form.get("bio") || null,
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleAddAvailability(professionalId, event) {
		event.preventDefault();
		setError("");
		setNotice("");
		const form = new FormData(event.target);
		try {
			await api.professionals.addAvailability(professionalId, {
				dayOfWeek: form.get("dayOfWeek"),
				startTime: `${form.get("startTime")}:00`,
				endTime: `${form.get("endTime")}:00`,
			});
			event.target.reset();
			setNotice("Horario agregado.");
		} catch (err) {
			setError(err.message);
		}
	}

	function branchName(id) {
		return branches.find((b) => b.id === id)?.name ?? id;
	}

	if (loading) return <p>Cargando...</p>;

	return (
		<div>
			<h1>Profesionales</h1>
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}
			{branches.length === 0 && <p className="muted">Cargá una sucursal primero.</p>}
			<form className="inline-form" onSubmit={handleCreate}>
				<select name="branchId" required defaultValue="">
					<option value="" disabled>
						Sucursal
					</option>
					{branches.map((b) => (
						<option key={b.id} value={b.id}>
							{b.name}
						</option>
					))}
				</select>
				<input name="displayName" placeholder="Nombre" required />
				<input name="bio" placeholder="Bio (opcional)" />
				<button type="submit">Agregar</button>
			</form>

			<div className="cards">
				{professionals.map((p) => (
					<div className="card" key={p.id}>
						<h3>{p.displayName}</h3>
						<p className="muted">{branchName(p.branchId)}</p>
						<p className="label">Agregar horario semanal</p>
						<form className="inline-form small" onSubmit={(event) => handleAddAvailability(p.id, event)}>
							<select name="dayOfWeek" required defaultValue="">
								<option value="" disabled>
									Día
								</option>
								{DAYS.map((d) => (
									<option key={d} value={d}>
										{d}
									</option>
								))}
							</select>
							<input name="startTime" type="time" required />
							<input name="endTime" type="time" required />
							<button type="submit">+ Horario</button>
						</form>
					</div>
				))}
			</div>
		</div>
	);
}
