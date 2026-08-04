import { useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";

export default function AccountPage() {
	const { me, refreshMe } = useAuth();
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [saving, setSaving] = useState(false);

	async function handleSave(event) {
		event.preventDefault();
		setError("");
		setSaving(true);
		const form = new FormData(event.target);
		try {
			await api.me.update({
				displayName: form.get("displayName")?.trim() || null,
				avatarUrl: form.get("avatarUrl")?.trim() || null,
			});
			await refreshMe();
			setNotice("Guardado.");
			setTimeout(() => setNotice(""), 3000);
		} catch (err) {
			setError(err.message);
		} finally {
			setSaving(false);
		}
	}

	if (!me) return <p>Cargando...</p>;

	return (
		<div>
			<h1>Mi cuenta</h1>
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}

			<div className="card">
				<div className="profile-header">
					{me.avatarUrl ? (
						<img src={me.avatarUrl} alt="" className="account-avatar" />
					) : (
						<span className="brand-mark account-avatar-fallback" aria-hidden="true">
							{(me.displayName || me.email).charAt(0).toUpperCase()}
						</span>
					)}
					<div>
						<h3>{me.displayName || me.email}</h3>
						<p className="muted">
							{me.email} · {me.role}
						</p>
					</div>
				</div>

				<form className="field-grid" style={{ marginTop: "1.1rem" }} onSubmit={handleSave}>
					<label>
						Nombre
						<input name="displayName" defaultValue={me.displayName ?? ""} placeholder="¿Cómo te llamás?" />
					</label>
					<label>
						URL de foto de perfil
						<input name="avatarUrl" defaultValue={me.avatarUrl ?? ""} placeholder="https://..." />
					</label>
					<div className="span-2 button-row">
						<button type="submit" disabled={saving}>
							{saving ? "Guardando..." : "Guardar"}
						</button>
					</div>
				</form>
			</div>

			<p className="label">Contraseña</p>
			<div className="card">
				<p className="muted">
					El cambio de contraseña todavía no es autoservicio — si necesitás una nueva, pedila directamente.
				</p>
			</div>
		</div>
	);
}
