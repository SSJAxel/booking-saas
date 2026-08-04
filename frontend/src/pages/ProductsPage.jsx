import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function ProductsPage() {
	const [products, setProducts] = useState([]);
	const [tenant, setTenant] = useState(null);
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
			await api.sales.create({ productId, quantity: Number(form.get("quantity")) });
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

	const limit = tenant?.planTier === "BASIC" ? 5 : null;

	return (
		<div>
			<h1>Productos</h1>
			{tenant && (
				<p className="muted">
					Plan {tenant.planTier}
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
