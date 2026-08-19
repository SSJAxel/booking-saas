import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import { PLAN_LIMITS } from "../planLimits.js";
import { ScissorsIcon, PlusIcon, EditIcon, TrashIcon } from "../components/icons.jsx";

const EMPTY_DRAFT = {
	name: "",
	description: "",
	category: "",
	durationMinutes: "",
	price: "",
	depositAmount: "",
	active: true,
	featured: false,
};

export default function ServicesPage() {
	const [services, setServices] = useState([]);
	const [professionals, setProfessionals] = useState([]);
	const [assignments, setAssignments] = useState({});
	const [tenant, setTenant] = useState(null);
	const [combos, setCombos] = useState([]);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [editingId, setEditingId] = useState(null);
	const [editDraft, setEditDraft] = useState(EMPTY_DRAFT);

	function flashNotice(message) {
		setNotice(message);
		setTimeout(() => setNotice(""), 3000);
	}

	async function refresh() {
		setLoading(true);
		try {
			const [s, p, t] = await Promise.all([api.services.list(), api.professionals.list(), api.tenant.get()]);
			setServices(s);
			setProfessionals(p);
			setTenant(t);
			const entries = await Promise.all(s.map(async (svc) => [svc.id, await api.services.listProfessionals(svc.id)]));
			setAssignments(Object.fromEntries(entries));
			// Combos are MAX-only server-side (ServiceComboService.requirePlanSupport) — only fetch/show
			// the section at all for a MAX tenant, same gate the backend itself enforces.
			if (t.planTier === "MAX") {
				setCombos(await api.serviceCombos.list());
			}
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
				category: form.get("category") || null,
				durationMinutes: Number(form.get("durationMinutes")),
				price: Number(form.get("price")),
				depositAmount: deposit ? Number(deposit) : null,
				featured: form.get("featured") === "on",
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

	function startEdit(s) {
		setEditingId(s.id);
		setEditDraft({
			name: s.name,
			description: s.description ?? "",
			category: s.category ?? "",
			durationMinutes: s.durationMinutes,
			price: s.price,
			depositAmount: s.depositAmount ?? "",
			active: s.active,
			featured: s.featured,
		});
	}

	async function handleSaveEdit(id) {
		setError("");
		setSaving(true);
		try {
			await api.services.update(id, {
				name: editDraft.name,
				description: editDraft.description || null,
				category: editDraft.category || null,
				durationMinutes: Number(editDraft.durationMinutes),
				price: Number(editDraft.price),
				depositAmount: editDraft.depositAmount ? Number(editDraft.depositAmount) : null,
				active: editDraft.active,
				featured: editDraft.featured,
			});
			setEditingId(null);
			await refresh();
			flashNotice("Servicio actualizado.");
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	async function handleDelete(id) {
		if (!window.confirm("¿Eliminar este servicio?")) return;
		setError("");
		try {
			await api.services.delete(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleCreateCombo(event) {
		event.preventDefault();
		setError("");
		const form = new FormData(event.target);
		const comboDepositAmount = form.get("comboDepositAmount");
		try {
			await api.serviceCombos.create({
				serviceAId: form.get("serviceAId"),
				serviceBId: form.get("serviceBId"),
				comboPrice: Number(form.get("comboPrice")),
				comboDepositAmount: comboDepositAmount ? Number(comboDepositAmount) : null,
			});
			event.target.reset();
			refresh();
			flashNotice("Combo creado.");
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDeleteCombo(id) {
		if (!window.confirm("¿Eliminar este combo?")) return;
		setError("");
		try {
			await api.serviceCombos.delete(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	function serviceName(id) {
		return services.find((s) => s.id === id)?.name ?? "(servicio eliminado)";
	}

	if (loading) return <p>Cargando...</p>;

	const limit = tenant && PLAN_LIMITS[tenant.planTier]?.maxServices;
	const atLimit = limit != null && services.length >= limit;

	return (
		<div>
			<div className="page-title">
				<span className="page-title-icon">
					<ScissorsIcon />
				</span>
				<h1>Servicios</h1>
			</div>
			{tenant && (
				<p className="muted">
					Plan {planLabel(tenant.planTier)} · {services.length}/{limit} servicios
				</p>
			)}
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}
			{atLimit ? (
				<p className="muted">Llegaste al límite de servicios de tu plan {planLabel(tenant.planTier)}.</p>
			) : (
				<form className="inline-form" onSubmit={handleCreate}>
					<input name="name" placeholder="Nombre" required />
					<input name="description" placeholder="Descripción" />
					<input name="category" placeholder="Categoría (ej: Cortes)" />
					<input name="durationMinutes" type="number" min="1" placeholder="Duración (min)" required />
					<input name="price" type="number" step="0.01" min="0" placeholder="Precio" required />
					<input name="depositAmount" type="number" step="0.01" min="0" placeholder="Seña (opcional)" />
					<label className="form-check">
						<input type="checkbox" name="featured" />
						Destacado
					</label>
					<button type="submit">
						<PlusIcon />
						Agregar
					</button>
				</form>
			)}

			<div className="cards">
				{services.map((s) =>
					editingId === s.id ? (
						<div className="card card-span-full" key={s.id}>
							<div className="field-grid">
								<label>
									Nombre
									<input
										value={editDraft.name}
										onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })}
									/>
								</label>
								<label>
									Duración (min)
									<input
										type="number"
										min="1"
										value={editDraft.durationMinutes}
										onChange={(e) => setEditDraft({ ...editDraft, durationMinutes: e.target.value })}
									/>
								</label>
								<label>
									Precio
									<input
										type="number"
										step="0.01"
										min="0"
										value={editDraft.price}
										onChange={(e) => setEditDraft({ ...editDraft, price: e.target.value })}
									/>
								</label>
								<label>
									Seña (opcional)
									<input
										type="number"
										step="0.01"
										min="0"
										value={editDraft.depositAmount}
										onChange={(e) => setEditDraft({ ...editDraft, depositAmount: e.target.value })}
									/>
								</label>
								<label>
									Categoría
									<input
										value={editDraft.category}
										onChange={(e) => setEditDraft({ ...editDraft, category: e.target.value })}
										placeholder="ej: Cortes"
									/>
								</label>
								<label className="span-2">
									Descripción
									<input
										value={editDraft.description}
										onChange={(e) => setEditDraft({ ...editDraft, description: e.target.value })}
									/>
								</label>
							</div>
							<div className="card-header" style={{ marginTop: "0.8rem" }}>
								<div className="button-row">
									<label className="form-check">
										<input
											type="checkbox"
											checked={editDraft.active}
											onChange={(e) => setEditDraft({ ...editDraft, active: e.target.checked })}
										/>
										Activo
									</label>
									<label className="form-check">
										<input
											type="checkbox"
											checked={editDraft.featured}
											onChange={(e) => setEditDraft({ ...editDraft, featured: e.target.checked })}
										/>
										Destacado
									</label>
								</div>
								<div className="button-row">
									<button type="button" disabled={saving} onClick={() => handleSaveEdit(s.id)}>
										{saving ? "Guardando..." : "Guardar"}
									</button>
									<button type="button" className="secondary" disabled={saving} onClick={() => setEditingId(null)}>
										Cancelar
									</button>
								</div>
							</div>
						</div>
					) : (
						<div className="card" key={s.id}>
							<h3>
								{s.featured && "★ "}
								{s.name}
								{!s.active && " (inactivo)"}
							</h3>
							{s.category && <p className="muted">Categoría: {s.category}</p>}
							<p className="muted">
								{s.durationMinutes} min · ${s.price}
								{s.depositAmount ? ` · seña $${s.depositAmount}` : " · sin seña (confirma solo)"}
							</p>
							<div className="button-row">
								<button type="button" className="secondary" onClick={() => startEdit(s)}>
									<EditIcon />
									Editar
								</button>
								<button type="button" className="danger" onClick={() => handleDelete(s.id)}>
									<TrashIcon />
									Eliminar
								</button>
							</div>
							<div className="card-section">
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
						</div>
					),
				)}
			</div>

			{tenant?.planTier === "MAX" && (
				<div className="card-section" style={{ marginTop: "2rem" }}>
					<h2>Precio de combo</h2>
					<p className="muted">
						Definí un precio (y opcionalmente una seña) combinados para un par de servicios reservados juntos —
						por ejemplo tatuaje + piercing a un precio distinto de la suma de cada uno por separado.
					</p>
					{services.length < 2 ? (
						<p className="muted">Necesitás al menos 2 servicios cargados para armar un combo.</p>
					) : (
						<form className="inline-form" onSubmit={handleCreateCombo}>
							<select name="serviceAId" required defaultValue="">
								<option value="" disabled>
									Servicio A
								</option>
								{services.map((s) => (
									<option key={s.id} value={s.id}>
										{s.name}
									</option>
								))}
							</select>
							<select name="serviceBId" required defaultValue="">
								<option value="" disabled>
									Servicio B
								</option>
								{services.map((s) => (
									<option key={s.id} value={s.id}>
										{s.name}
									</option>
								))}
							</select>
							<input name="comboPrice" type="number" step="0.01" min="0" placeholder="Precio combo" required />
							<input
								name="comboDepositAmount"
								type="number"
								step="0.01"
								min="0"
								placeholder="Seña combo (opcional)"
							/>
							<button type="submit">
								<PlusIcon />
								Agregar combo
							</button>
						</form>
					)}
					<div className="cards">
						{combos.map((c) => (
							<div className="card" key={c.id}>
								<h3>
									{serviceName(c.serviceAId)} + {serviceName(c.serviceBId)}
								</h3>
								<p className="muted">
									Precio combo: ${c.comboPrice}
									{c.comboDepositAmount != null ? ` · seña combo $${c.comboDepositAmount}` : " · seña sin cambios"}
								</p>
								<div className="button-row">
									<button type="button" className="danger" onClick={() => handleDeleteCombo(c.id)}>
										<TrashIcon />
										Eliminar
									</button>
								</div>
							</div>
						))}
					</div>
				</div>
			)}
		</div>
	);
}
