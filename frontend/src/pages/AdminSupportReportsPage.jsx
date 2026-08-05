import { useEffect, useState } from "react";
import { api } from "../api.js";

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

	return (
		<div>
			<h1>Reportes de error</h1>
			{error && <p className="error">{error}</p>}
			{loading ? (
				<p>Cargando...</p>
			) : reports.length === 0 ? (
				<p className="muted">No hay reportes todavía.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Tenant</th>
							<th>Reportado por</th>
							<th>Mensaje</th>
							<th>Fecha</th>
							<th>Imagen</th>
							<th>Resuelto</th>
						</tr>
					</thead>
					<tbody>
						{reports.map((r) => (
							<tr key={r.id}>
								<td>
									{r.tenantName}
									<br />
									<span className="muted">{r.tenantSlug}</span>
								</td>
								<td>{r.submitterEmail}</td>
								<td>{r.message}</td>
								<td>{new Date(r.createdAt).toLocaleString()}</td>
								<td>
									<ReportImageThumbnail reportId={r.id} />
								</td>
								<td>
									<input
										type="checkbox"
										checked={r.resolved}
										onChange={(event) => handleToggleResolved(r.id, event.target.checked)}
									/>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
