import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function ReportBugModal({ open, onClose }) {
	const [message, setMessage] = useState("");
	const [image, setImage] = useState(null);
	const [error, setError] = useState("");
	const [sending, setSending] = useState(false);
	const [sent, setSent] = useState(false);

	useEffect(() => {
		if (!open) return;
		setMessage("");
		setImage(null);
		setError("");
		setSending(false);
		setSent(false);
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [open, onClose]);

	if (!open) return null;

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		setSending(true);
		try {
			await api.support.report(message, image);
			setSent(true);
			setTimeout(onClose, 1500);
		} catch (err) {
			setError(err.message);
		} finally {
			setSending(false);
		}
	}

	return (
		<div className="modal-backdrop" onClick={onClose}>
			<div className="modal-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
				<div className="modal-header">
					<h2>Reportar un problema</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar">
						×
					</button>
				</div>
				<div className="modal-body">
					{sent ? (
						<p className="notice">Gracias, ya nos llegó tu reporte.</p>
					) : (
						<form className="inline-form" onSubmit={handleSubmit}>
							<label>
								¿Qué pasó?
								<textarea
									value={message}
									onChange={(event) => setMessage(event.target.value)}
									required
									rows={4}
								/>
							</label>
							<label>
								Captura de pantalla
								<input
									type="file"
									accept="image/*"
									required
									onChange={(event) => setImage(event.target.files[0])}
								/>
							</label>
							{error && <p className="error">{error}</p>}
							<button type="submit" disabled={sending}>
								{sending ? "Enviando..." : "Enviar"}
							</button>
						</form>
					)}
				</div>
			</div>
		</div>
	);
}
