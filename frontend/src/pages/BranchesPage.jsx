import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import { PLAN_LIMITS } from "../planLimits.js";
import WeeklySchedule from "../components/WeeklySchedule.jsx";

export default function BranchesPage() {
	const [branches, setBranches] = useState([]);
	const [tenant, setTenant] = useState(null);
	const [hoursByBranch, setHoursByBranch] = useState({});
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [editingId, setEditingId] = useState(null);
	const [editDraft, setEditDraft] = useState({ name: "", address: "", phone: "", active: true });

	function flashNotice(message) {
		setNotice(message);
		setTimeout(() => setNotice(""), 3000);
	}

	async function refresh() {
		setLoading(true);
		try {
			const [list, t] = await Promise.all([api.branches.list(), api.tenant.get()]);
			setBranches(list);
			setTenant(t);
			const entries = await Promise.all(list.map(async (b) => [b.id, await api.branches.listHours(b.id)]));
			setHoursByBranch(Object.fromEntries(entries));
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
			await api.branches.create({
				name: form.get("name"),
				address: form.get("address") || null,
				phone: form.get("phone") || null,
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	function startEdit(branch) {
		setEditingId(branch.id);
		setEditDraft({ name: branch.name, address: branch.address ?? "", phone: branch.phone ?? "", active: branch.active });
	}

	async function handleSaveEdit(id) {
		setError("");
		setSaving(true);
		try {
			await api.branches.update(id, {
				name: editDraft.name,
				address: editDraft.address || null,
				phone: editDraft.phone || null,
				active: editDraft.active,
			});
			setEditingId(null);
			await refresh();
			flashNotice("Sucursal actualizada.");
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	async function handleDelete(id) {
		if (
			!window.confirm(
				"¿Eliminar esta sucursal? Esta acción es permanente: también se borran sus profesionales (con sus " +
					"horarios, bloqueos y asignaciones de servicio), sus horarios propios, y TODOS los turnos (pasados y " +
					"futuros) reservados ahí. No se puede deshacer. Si preferís conservar el historial, usá Editar → Activa " +
					"para desactivarla en vez de eliminarla.",
			)
		)
			return;
		setError("");
		try {
			await api.branches.delete(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	if (loading) return <p>Cargando...</p>;

	const limit = tenant && PLAN_LIMITS[tenant.planTier]?.maxBranches;
	const atLimit = limit != null && branches.length >= limit;

	return (
		<div>
			<h1>Sucursales</h1>
			{tenant && (
				<p className="muted">
					Plan {planLabel(tenant.planTier)} · {branches.length}/{limit} sucursales
				</p>
			)}
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}
			{atLimit ? (
				<p className="muted">Llegaste al límite de sucursales de tu plan {planLabel(tenant.planTier)}.</p>
			) : (
				<form className="inline-form" onSubmit={handleCreate}>
					<input name="name" placeholder="Nombre" required />
					<input name="address" placeholder="Dirección" />
					<input name="phone" placeholder="Teléfono" />
					<button type="submit">Agregar</button>
				</form>
			)}

			{branches.length === 0 ? (
				<p className="muted">Todavía no hay sucursales.</p>
			) : (
				<div className="cards stacked">
					{branches.map((b) => (
						<div className="card" key={b.id}>
							{editingId === b.id ? (
								<>
									<div className="field-grid">
										<label>
											Nombre
											<input
												value={editDraft.name}
												onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })}
											/>
										</label>
										<label>
											Dirección
											<input
												value={editDraft.address}
												onChange={(e) => setEditDraft({ ...editDraft, address: e.target.value })}
											/>
										</label>
										<label className="span-2">
											Teléfono
											<input
												value={editDraft.phone}
												onChange={(e) => setEditDraft({ ...editDraft, phone: e.target.value })}
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
											Activa
										</label>
										<div className="button-row">
											<button type="button" disabled={saving} onClick={() => handleSaveEdit(b.id)}>
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
										<h3>{b.name}</h3>
										<p className="muted">
											{[b.address, b.phone].filter(Boolean).join(" · ") || "Sin dirección ni teléfono"}
											{!b.active && " · inactiva"}
										</p>
									</div>
									<div className="button-row">
										<button type="button" className="secondary" onClick={() => startEdit(b)}>
											Editar
										</button>
										<button type="button" className="danger" onClick={() => handleDelete(b.id)}>
											Eliminar
										</button>
									</div>
								</div>
							)}

							<div className="card-section">
								<p className="label">Horarios habilitados</p>
								<WeeklySchedule
									entries={hoursByBranch[b.id] ?? []}
									onCreate={(body) => api.branches.addHours(b.id, body)}
									onDelete={(hoursId) => api.branches.deleteHours(b.id, hoursId)}
									onSaved={() => {
										refresh();
										flashNotice("Horarios actualizados.");
									}}
									onError={setError}
								/>
							</div>
						</div>
					))}
				</div>
			)}
		</div>
	);
}
