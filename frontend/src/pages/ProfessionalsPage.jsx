import { useEffect, useState } from "react";
import { api, resolveMediaUrl } from "../api.js";
import { planLabel } from "../labels.js";
import { PLAN_LIMITS } from "../planLimits.js";
import WeeklySchedule from "../components/WeeklySchedule.jsx";
import MonthCalendar from "../components/MonthCalendar.jsx";
import { professionalColor } from "../professionalColor.js";

export default function ProfessionalsPage() {
	const [branches, setBranches] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [tenant, setTenant] = useState(null);
	const [timeOffByProfessional, setTimeOffByProfessional] = useState({});
	const [availabilityByProfessional, setAvailabilityByProfessional] = useState({});
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [editingId, setEditingId] = useState(null);
	const [editDraft, setEditDraft] = useState({ displayName: "", bio: "", photoUrl: "", active: true, branchId: "" });
	const [uploadingPhotoFor, setUploadingPhotoFor] = useState(null);
	const [rangePickerFor, setRangePickerFor] = useState(null);
	const [selectedDates, setSelectedDates] = useState(new Set());
	const [rangeReason, setRangeReason] = useState("");

	function flashNotice(message) {
		setNotice(message);
		setTimeout(() => setNotice(""), 3000);
	}

	async function refresh() {
		setLoading(true);
		try {
			const [b, p, t] = await Promise.all([api.branches.list(), api.professionals.list(), api.tenant.get()]);
			setBranches(b);
			setProfessionals(p);
			setTenant(t);
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
		setEditDraft({
			displayName: p.displayName,
			bio: p.bio ?? "",
			photoUrl: p.photoUrl ?? "",
			active: p.active,
			branchId: p.branchId,
		});
	}

	async function handleSaveEdit(id) {
		setError("");
		setSaving(true);
		try {
			await api.professionals.update(id, {
				branchId: editDraft.branchId,
				displayName: editDraft.displayName,
				bio: editDraft.bio || null,
				photoUrl: editDraft.photoUrl || null,
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

	async function handleUploadPhoto(id, event) {
		const file = event.target.files[0];
		if (!file) return;
		setError("");
		setUploadingPhotoFor(id);
		try {
			await api.professionals.uploadPhoto(id, file);
			await refresh();
		} catch (err) {
			setError(err.message);
		} finally {
			setUploadingPhotoFor(null);
			event.target.value = "";
		}
	}

	async function handleRemovePhoto(professional) {
		setError("");
		try {
			await api.professionals.update(professional.id, {
				branchId: professional.branchId,
				displayName: professional.displayName,
				bio: professional.bio,
				photoUrl: null,
				active: professional.active,
			});
			await refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDelete(id) {
		if (
			!window.confirm(
				"¿Eliminar este profesional? Esta acción es permanente: también se borran sus horarios, bloqueos, " +
					"asignaciones de servicio y TODOS los turnos (pasados y futuros) reservados con él/ella. No se puede " +
					"deshacer. Si preferís conservar el historial, usá Editar → Activo para desactivarlo en vez de eliminarlo.",
			)
		)
			return;
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

	function openRangePicker(professionalId) {
		setRangePickerFor(professionalId);
		setSelectedDates(new Set());
		setRangeReason("");
	}

	function closeRangePicker() {
		setRangePickerFor(null);
		setSelectedDates(new Set());
		setRangeReason("");
	}

	function toggleDate(dateKey) {
		setSelectedDates((prev) => {
			const next = new Set(prev);
			if (next.has(dateKey)) next.delete(dateKey);
			else next.add(dateKey);
			return next;
		});
	}

	async function handleAddMultiDayTimeOff(professionalId) {
		const dates = [...selectedDates];
		if (dates.length === 0) return;
		setError("");
		try {
			await Promise.all(
				dates.map((date) =>
					api.professionals.addTimeOff(professionalId, {
						date,
						startTime: null,
						endTime: null,
						reason: rangeReason || null,
					}),
				),
			);
			closeRangePicker();
			await refresh();
			flashNotice(`${dates.length} bloqueo${dates.length === 1 ? "" : "s"} agregado${dates.length === 1 ? "" : "s"}.`);
		} catch (err) {
			setError(err.message);
		}
	}

	function branchName(id) {
		return branches.find((b) => b.id === id)?.name ?? id;
	}

	if (loading) return <p>Cargando...</p>;

	const limit = tenant && PLAN_LIMITS[tenant.planTier]?.maxProfessionals;
	const atLimit = limit != null && professionals.length >= limit;

	return (
		<div>
			<h1>Profesionales</h1>
			{tenant && (
				<p className="muted">
					Plan {planLabel(tenant.planTier)} · {professionals.length}/{limit} profesionales
				</p>
			)}
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}
			{branches.length === 0 && <p className="muted">Cargá una sucursal primero.</p>}
			{atLimit ? (
				<p className="muted">Llegaste al límite de profesionales de tu plan {planLabel(tenant.planTier)}.</p>
			) : (
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
			)}
			{branches.length > 0 && !atLimit && (
				<p className="muted" style={{ marginTop: "-0.4rem" }}>
					La foto se sube después de crear el profesional, desde su tarjeta.
				</p>
			)}

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
									<h3>
										{p.photoUrl ? (
											<img
												src={resolveMediaUrl(p.photoUrl)}
												alt={p.displayName}
												style={{
													width: "24px",
													height: "24px",
													borderRadius: "50%",
													objectFit: "cover",
													marginRight: "0.5rem",
													verticalAlign: "middle",
												}}
											/>
										) : (
											<span
												className="appt-color-dot"
												style={{
													background: professionalColor(p.id),
													width: "10px",
													height: "10px",
													marginRight: "0.5rem",
												}}
											/>
										)}
										{p.displayName}
									</h3>
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
							<p className="label">Foto</p>
							<div className="button-row" style={{ alignItems: "center", gap: "0.5rem" }}>
								<input
									type="file"
									accept="image/*"
									disabled={uploadingPhotoFor === p.id}
									onChange={(e) => handleUploadPhoto(p.id, e)}
								/>
								{uploadingPhotoFor === p.id && <span className="muted">Subiendo...</span>}
								{p.photoUrl && (
									<button type="button" className="secondary" onClick={() => handleRemovePhoto(p)}>
										Quitar
									</button>
								)}
							</div>
						</div>

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

							{rangePickerFor === p.id ? (
								<div className="add-form" style={{ flexDirection: "column", alignItems: "stretch" }}>
									<MonthCalendar selected={selectedDates} onToggle={toggleDate} />
									<input
										value={rangeReason}
										onChange={(event) => setRangeReason(event.target.value)}
										placeholder="Motivo (opcional, ej: Vacaciones)"
									/>
									<div className="button-row">
										<button
											type="button"
											disabled={selectedDates.size === 0}
											onClick={() => handleAddMultiDayTimeOff(p.id)}
										>
											{selectedDates.size === 0
											? "Bloquear días seleccionados"
											: `Bloquear ${selectedDates.size} día${selectedDates.size === 1 ? "" : "s"}`}
										</button>
										<button type="button" className="secondary" onClick={closeRangePicker}>
											Cancelar
										</button>
									</div>
								</div>
							) : (
								<button
									type="button"
									className="secondary"
									style={{ marginTop: "0.6rem" }}
									onClick={() => openRangePicker(p.id)}
								>
									Bloquear varios días completos (vacaciones, licencia...)
								</button>
							)}
						</div>
					</div>
				))}
			</div>
		</div>
	);
}
