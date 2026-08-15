import { useEffect, useState } from "react";
import { api } from "../api.js";
import { planLabel } from "../labels.js";
import { PLAN_LIMITS, planHasCommissions, planHasProducts } from "../planLimits.js";

export default function ProductsPage() {
	const [products, setProducts] = useState([]);
	const [tenant, setTenant] = useState(null);
	const [professionals, setProfessionals] = useState([]);
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [editingId, setEditingId] = useState(null);
	const [editDraft, setEditDraft] = useState({ name: "", price: "", stock: "", active: true });

	function flashNotice(message) {
		setNotice(message);
		setTimeout(() => setNotice(""), 3000);
	}

	async function refresh() {
		setLoading(true);
		try {
			const [p, t] = await Promise.all([api.products.list(), api.tenant.get()]);
			setProducts(p);
			setTenant(t);
			// GET /api/professionals is OWNER/ADMIN-only, but this page (and selling a product) is
			// also open to STAFF — a 403 here is expected for them, not a real error, so it's caught
			// on its own rather than surfacing the page-wide error banner. STAFF just won't see the
			// "which professional" picker, same as before this feature existed.
			if (planHasCommissions(t.planTier) && t.commissionsEnabled) {
				try {
					setProfessionals(await api.professionals.list());
				} catch {
					setProfessionals([]);
				}
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
		try {
			await api.products.create({
				name: form.get("name"),
				price: Number(form.get("price")),
				stock: Number(form.get("stock")),
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleSell(productId, event) {
		event.preventDefault();
		setError("");
		const form = new FormData(event.target);
		try {
			await api.sales.create({
				productId,
				quantity: Number(form.get("quantity")),
				professionalId: form.get("professionalId") || null,
			});
			event.target.reset();
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	function startEdit(p) {
		setEditingId(p.id);
		setEditDraft({ name: p.name, price: p.price, stock: p.stock, active: p.active });
	}

	async function handleSaveEdit(id) {
		setError("");
		setSaving(true);
		try {
			await api.products.update(id, {
				name: editDraft.name,
				price: Number(editDraft.price),
				stock: Number(editDraft.stock),
				active: editDraft.active,
			});
			setEditingId(null);
			await refresh();
			flashNotice("Producto actualizado.");
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	async function handleDelete(id) {
		if (!window.confirm("¿Eliminar este producto?")) return;
		setError("");
		try {
			await api.products.delete(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	if (loading) return <p>Cargando...</p>;

	if (tenant && !planHasProducts(tenant.planTier)) {
		return (
			<div>
				<h1>Productos</h1>
				<p className="muted">
					Tu plan {planLabel(tenant.planTier)} no incluye venta de productos/stock. Mejorá tu plan desde
					"Mi Plan" para habilitar esta sección.
				</p>
			</div>
		);
	}

	const limit = tenant && PLAN_LIMITS[tenant.planTier]?.maxProducts;

	return (
		<div>
			<h1>Productos</h1>
			{tenant && (
				<p className="muted">
					Plan {planLabel(tenant.planTier)}
					{limit ? ` · ${products.length}/${limit} productos activos` : ` · ${products.length} productos`}
				</p>
			)}
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}
			<form className="inline-form" onSubmit={handleCreate}>
				<input name="name" placeholder="Nombre" required />
				<input name="price" type="number" step="0.01" min="0" placeholder="Precio" required />
				<input name="stock" type="number" min="0" placeholder="Stock" required />
				<button type="submit">Agregar</button>
			</form>

			{products.length === 0 ? (
				<p className="muted">Todavía no hay productos.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Nombre</th>
							<th>Precio</th>
							<th>Stock</th>
							<th>Activo</th>
							<th>Vender</th>
							<th>Acciones</th>
						</tr>
					</thead>
					<tbody>
						{products.map((p) =>
							editingId === p.id ? (
								<tr key={p.id}>
									<td>
										<input
											aria-label="Nombre"
											value={editDraft.name}
											onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })}
										/>
									</td>
									<td>
										<input
											aria-label="Precio"
											type="number"
											step="0.01"
											min="0"
											value={editDraft.price}
											onChange={(e) => setEditDraft({ ...editDraft, price: e.target.value })}
										/>
									</td>
									<td>
										<input
											aria-label="Stock"
											type="number"
											min="0"
											value={editDraft.stock}
											onChange={(e) => setEditDraft({ ...editDraft, stock: e.target.value })}
										/>
									</td>
									<td>
										<input
											aria-label="Activo"
											type="checkbox"
											checked={editDraft.active}
											onChange={(e) => setEditDraft({ ...editDraft, active: e.target.checked })}
										/>
									</td>
									<td>—</td>
									<td className="button-row">
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
									</td>
								</tr>
							) : (
								<tr key={p.id}>
									<td>{p.name}</td>
									<td>${p.price}</td>
									<td>{p.stock}</td>
									<td>{p.active ? "Sí" : "No"}</td>
									<td>
										<form className="inline-form small" onSubmit={(event) => handleSell(p.id, event)}>
											<input name="quantity" type="number" min="1" defaultValue="1" required />
											{professionals.length > 0 && (
												<select name="professionalId" defaultValue="" title="Quién la vendió (para comisión)">
													<option value="">Sin asignar</option>
													{professionals.map((pr) => (
														<option key={pr.id} value={pr.id}>
															{pr.displayName}
														</option>
													))}
												</select>
											)}
											<button type="submit" disabled={p.stock === 0}>
												Vender
											</button>
										</form>
									</td>
									<td className="button-row">
										<button type="button" className="secondary" onClick={() => startEdit(p)}>
											Editar
										</button>
										<button type="button" className="danger" onClick={() => handleDelete(p.id)}>
											Eliminar
										</button>
									</td>
								</tr>
							),
						)}
					</tbody>
				</table>
			)}
		</div>
	);
}
