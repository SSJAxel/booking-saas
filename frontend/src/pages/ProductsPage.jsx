import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function ProductsPage() {
	const [products, setProducts] = useState([]);
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

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
							<th>Vender</th>
						</tr>
					</thead>
					<tbody>
						{products.map((p) => (
							<tr key={p.id}>
								<td>{p.name}</td>
								<td>${p.price}</td>
								<td>{p.stock}</td>
								<td>
									<form className="inline-form small" onSubmit={(event) => handleSell(p.id, event)}>
										<input name="quantity" type="number" min="1" defaultValue="1" required />
										<button type="submit" disabled={p.stock === 0}>
											Vender
										</button>
									</form>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
