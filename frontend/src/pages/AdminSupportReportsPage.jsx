import { useEffect, useState } from "react";
import { api } from "../api.js";

const TYPE_LABELS = { BUG: "🐛 Bug", PLAN_UPGRADE: "⭐ Mejorar plan" };

/** The image endpoint requires a Bearer token, which an <img src> can't carry — fetch it as a
 * blob and hand the browser an object URL instead, revoking it on unmount to avoid leaking it. */
function ReportImageThumbnail({ reportId }) {
	const [url, setUrl] = useState(null);

	useEffect(() => {
		let objectUrl;
		api.admin
			.reportImageBlob(reportId)
			.then((blob) => {
				objectUrl = URL.createObjectURL(blob);
				setUrl(objectUrl);
			})
			.catch(() => {});
		return () => {
			if (objectUrl) URL.revokeObjectURL(objectUrl);
		};
	}, [reportId]);

	if (!url) return <span className="muted">Cargando...</span>;
	return (
		<a href={url} target="_blank" rel="noreferrer">
			<img src={url} alt="Captura del reporte" style={{ maxWidth: "80px", maxHeight: "80px", borderRadius: "4px" }} />
		</a>
	);
}

export default function AdminSupportReportsPage() {
	const [reports, setReports] = useState([]);
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);

	function refresh() {
		return api.admin
			.supportReports()
			.then(setReports)
			.catch((err) => setError(err.message));
	}

	useEffect(() => {
		refresh().finally(() => setLoading(false));
	}, []);

	async function handleToggleResolved(id, resolved) {
		setError("");
		try {
			await api.admin.resolveReport(id, resolved);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleDelete(id) {
		if (!window.confirm("¿Eliminar este reporte para siempre? No se puede deshacer.")) return;
		setError("");
		try {
			await api.admin.deleteReport(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	if (loading) return <p>Cargando...</p>;

	const pending = reports.filter((r) => !r.resolved);
	const history = reports.filter((r) => r.resolved);

	return (
		<div>
			<h1>Reportes</h1>
			{error && <p className="error">{error}</p>}
			<ReportTable
				title="Pendientes"
				reports={pending}
				emptyMessage="No hay nada pendiente."
				onToggleResolved={handleToggleResolved}
				onDelete={handleDelete}
			/>
			<ReportTable
				title="Historial"
				reports={history}
				emptyMessage="Todavía no se resolvió nada."
				onToggleResolved={handleToggleResolved}
				onDelete={handleDelete}
			/>
		</div>
	);
}

function ReportTable({ title, reports, emptyMessage, onToggleResolved, onDelete }) {
	return (
		<section className="appointments-section">
			<h2>
				{title} <span className="muted">({reports.length})</span>
			</h2>
			{reports.length === 0 ? (
				<p className="muted">{emptyMessage}</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Tipo</th>
							<th>Tenant</th>
							<th>Reportado por</th>
							<th>Mensaje</th>
							<th>Fecha</th>
							<th>Imagen</th>
							<th>Resuelto</th>
							<th></th>
						</tr>
					</thead>
					<tbody>
						{reports.map((r) => (
							<tr key={r.id}>
								<td>{TYPE_LABELS[r.type] ?? r.type}</td>
								<td>
									{r.tenantName}
									<br />
									<span className="muted">{r.tenantSlug}</span>
								</td>
								<td>{r.submitterEmail}</td>
								<td>{r.message}</td>
								<td>{new Date(r.createdAt).toLocaleString()}</td>
								<td>{r.hasImage ? <ReportImageThumbnail reportId={r.id} /> : <span className="muted">—</span>}</td>
								<td>
									<input
										type="checkbox"
										checked={r.resolved}
										onChange={(event) => onToggleResolved(r.id, event.target.checked)}
									/>
								</td>
								<td>
									<button type="button" className="link-button" onClick={() => onDelete(r.id)}>
										Eliminar
									</button>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</section>
	);
}
