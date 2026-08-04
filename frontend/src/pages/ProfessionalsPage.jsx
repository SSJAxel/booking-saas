import { useEffect, useState } from "react";
import { api } from "../api.js";
import WeeklySchedule from "../components/WeeklySchedule.jsx";

export default function ProfessionalsPage() {
	const [branches, setBranches] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [timeOffByProfessional, setTimeOffByProfessional] = useState({});
	const [availabilityByProfessional, setAvailabilityByProfessional] = useState({});
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [editingId, setEditingId] = useState(null);
	const [editDraft, setEditDraft] = useState({ displayName: "", bio: "", active: true, branchId: "" });

	function flashNotice(message) {
		setNotice(message);
		setTimeout(() => setNotice(""), 3000);
	}

	async function refresh() {
		setLoading(true);
		try {
			const [b, p] = await Promise.all([api.branches.list(), api.professionals.list()]);
			setBranches(b);
			setProfessionals(p);
			const [timeOffEntries, availabilityEntries] = await Promise.all([
				Promise.all(p.map(async (pr) => [pr.id, await api.professionals.listTimeOff(pr.id)])),
				Promise.all(p.map(async (pr) => [pr.id, await api.professionals.listAvailability(pr.id)])),
			]);
			setTimeOffByProfessional(Object.fromEntries(timeOffEntries));
			setAvailabilityByProfessional(Object.fromEntries(availabilityEntries));
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

	function startEdit(p) {
		setEditingId(p.id);
		setEditDraft({ displayName: p.displayName, bio: p.bio ?? "", active: p.active, branchId: p.branchId });
	}

	async function handleSaveEdit(id) {
		setError("");
		setSaving(true);
		try {
			await api.professionals.update(id, {
				branchId: editDraft.branchId,
				displayName: editDraft.displayName,
				bio: editDraft.bio || null,
				active: editDraft.active,
			});
			setEditingId(null);
			await refresh();
			flashNotice("Profesional actualizado.");
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	async function handleDelete(id) {
		if (!window.confirm("¿Eliminar este profesional?")) return;
		setError("");
		try {
			await api.professionals.delete(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleAddTimeOff(professionalId, event) {
		event.preventDefault();
		setError("");
		const form = new FormData(event.target);
		const startTime = form.get("startTime");
		const endTime = form.get("endTime");
		try {
			await api.professionals.addTimeOff(professionalId, {
				date: form.get("date"),
				startTime: startTime ? `${startTime}:00` : null,
				endTime: endTime ? `${endTime}:00` : null,
				reason: form.get("reason") || null,
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDeleteTimeOff(professionalId, timeOffId) {
		if (!window.confirm("¿Eliminar este bloqueo?")) return;
		setError("");
		try {
			await api.professionals.deleteTimeOff(professionalId, timeOffId);
			refresh();
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

			<div className="cards stacked">
				{professionals.map((p) => (
					<div className="card" key={p.id}>
						{editingId === p.id ? (
							<>
								<div className="field-grid">
									<label>
										Nombre
										<input
											value={editDraft.displayName}
											onChange={(e) => setEditDraft({ ...editDraft, displayName: e.target.value })}
										/>
									</label>
									<label>
										Sucursal
										<select
											value={editDraft.branchId}
											onChange={(e) => setEditDraft({ ...editDraft, branchId: e.target.value })}
										>
											{branches.map((b) => (
												<option key={b.id} value={b.id}>
													{b.name}
												</option>
											))}
										</select>
									</label>
									<label className="span-2">
										Bio
										<input
											value={editDraft.bio}
											onChange={(e) => setEditDraft({ ...editDraft, bio: e.target.value })}
										/>
									</label>
								</div>
								<div className="card-header" style={{ marginTop: "0.8rem" }}>
									<label className="form-check">
										<input
											type="checkbox"
											checked={editDraft.active}
											onChange={(e) => setEditDraft({ ...editDraft, active: e.target.checked })}
										/>
										Activo
									</label>
									<div className="button-row">
										<button type="button" disabled={saving} onClick={() => handleSaveEdit(p.id)}>
											{saving ? "Guardando..." : "Guardar"}
										</button>
										<button
											type="button"
											className="secondary"
											disabled={saving}
											onClick={() => setEditingId(null)}
										>
											Cancelar
										</button>
									</div>
								</div>
							</>
						) : (
							<div className="card-header">
								<div>
									<h3>{p.displayName}</h3>
									<p className="muted">
										{branchName(p.branchId)}
										{!p.active && " · inactivo"}
									</p>
								</div>
								<div className="button-row">
									<button type="button" className="secondary" onClick={() => startEdit(p)}>
										Editar
									</button>
									<button type="button" className="danger" onClick={() => handleDelete(p.id)}>
										Eliminar
									</button>
								</div>
							</div>
						)}

						<div className="card-section">
							<p className="label">Horario semanal</p>
							<WeeklySchedule
								entries={availabilityByProfessional[p.id] ?? []}
								onCreate={(body) => api.professionals.addAvailability(p.id, body)}
								onDelete={(availabilityId) => api.professionals.deleteAvailability(p.id, availabilityId)}
								onSaved={() => {
									refresh();
									flashNotice("Horario actualizado.");
								}}
								onError={setError}
							/>
						</div>

						<div className="card-section">
							<p className="label">Bloqueos (día libre, médico, vacaciones...)</p>
							<div className="chip-row">
								{(timeOffByProfessional[p.id] ?? []).length === 0 && (
									<span className="muted">Sin bloqueos cargados.</span>
								)}
								{(timeOffByProfessional[p.id] ?? []).map((t) => (
									<button
										key={t.id}
										type="button"
										className="chip chip-removable"
										aria-label={`Eliminar bloqueo ${t.date}`}
										title="Eliminar"
										onClick={() => handleDeleteTimeOff(p.id, t.id)}
									>
										{t.date} {t.startTime ? `${t.startTime.slice(0, 5)}–${t.endTime.slice(0, 5)}` : "Todo el día"} ×
									</button>
								))}
							</div>
							<form className="add-form" onSubmit={(event) => handleAddTimeOff(p.id, event)}>
								<input name="date" type="date" required />
								<input name="startTime" type="time" placeholder="Desde (opcional)" />
								<input name="endTime" type="time" placeholder="Hasta (opcional)" />
								<input name="reason" placeholder="Motivo (opcional)" />
								<button type="submit">+ Bloqueo</button>
							</form>
						</div>
					</div>
				))}
			</div>
		</div>
	);
}
