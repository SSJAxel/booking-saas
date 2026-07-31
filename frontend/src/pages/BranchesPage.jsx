import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function BranchesPage() {
	const [branches, setBranches] = useState([]);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	async function refresh() {
		setLoading(true);
		try {
			setBranches(await api.branches.list());
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

	return (
		<div>
			<h1>Sucursales</h1>
			{error && <p className="error">{error}</p>}
			<form className="inline-form" onSubmit={handleCreate}>
				<input name="name" placeholder="Nombre" required />
				<input name="address" placeholder="Dirección" />
				<input name="phone" placeholder="Teléfono" />
				<button type="submit">Agregar</button>
			</form>
			{loading ? (
				<p>Cargando...</p>
			) : branches.length === 0 ? (
				<p className="muted">Todavía no hay sucursales.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Nombre</th>
							<th>Dirección</th>
							<th>Teléfono</th>
							<th>Activa</th>
						</tr>
					</thead>
					<tbody>
						{branches.map((b) => (
							<tr key={b.id}>
								<td>{b.name}</td>
								<td>{b.address}</td>
								<td>{b.phone}</td>
								<td>{b.active ? "Sí" : "No"}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
