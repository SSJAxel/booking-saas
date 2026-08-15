import { useEffect, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { UserIcon } from "../components/icons.jsx";

// Placeholder avatar set — see README "Avatares de perfil" for the designer handoff. Replacing
// these files under frontend/public/avatars/ with real artwork (same filenames) is enough, no
// code change needed here unless the count/filenames change.
const AVATAR_OPTIONS = Array.from({ length: 8 }, (_, i) => `/avatars/avatar-${i + 1}.svg`);

export default function AccountPage() {
	const { me, refreshMe } = useAuth();
	const [error, setError] = useState("");
	const [notice, setNotice] = useState("");
	const [saving, setSaving] = useState(false);
	const [avatarUrl, setAvatarUrl] = useState(null);

	// `me` loads asynchronously (AuthContext.refreshMe), so it's still null on the first render —
	// a plain useState(me?.avatarUrl) initializer would permanently lock in `null` and silently
	// wipe the user's saved avatar on their next save. Resync whenever the server value changes.
	useEffect(() => {
		setAvatarUrl(me?.avatarUrl ?? null);
	}, [me?.avatarUrl]);

	async function handleSave(event) {
		event.preventDefault();
		setError("");
		setSaving(true);
		const form = new FormData(event.target);
		try {
			await api.me.update({
				displayName: form.get("displayName")?.trim() || null,
				avatarUrl,
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
			<div className="page-title">
				<span className="page-title-icon">
					<UserIcon />
				</span>
				<h1>Mi cuenta</h1>
			</div>
			{error && <p className="error">{error}</p>}
			{notice && <p className="notice">{notice}</p>}

			<div className="card">
				<div className="profile-header">
					{avatarUrl ? (
						<img src={avatarUrl} alt="" className="account-avatar" />
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
					<div className="span-2">
						<label style={{ marginBottom: "0.5rem" }}>Foto de perfil</label>
						<div className="avatar-grid">
							<button
								type="button"
								className={`avatar-option${avatarUrl === null ? " selected" : ""}`}
								onClick={() => setAvatarUrl(null)}
								title="Sin avatar (usa tu inicial)"
							>
								<span className="brand-mark account-avatar-fallback" aria-hidden="true">
									{(me.displayName || me.email).charAt(0).toUpperCase()}
								</span>
							</button>
							{AVATAR_OPTIONS.map((url) => (
								<button
									key={url}
									type="button"
									className={`avatar-option${avatarUrl === url ? " selected" : ""}`}
									onClick={() => setAvatarUrl(url)}
								>
									<img src={url} alt="" />
								</button>
							))}
						</div>
					</div>
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
