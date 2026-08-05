import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";

export default function LoginPage() {
	const [mode, setMode] = useState("login");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const [registered, setRegistered] = useState(null);
	const [resendNotice, setResendNotice] = useState("");
	const { login, register } = useAuth();
	const navigate = useNavigate();

	async function handleLogin(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			const res = await login({
				tenantSlug: form.get("tenantSlug"),
				email: form.get("email"),
				password: form.get("password"),
			});
			navigate(res.platformAdmin ? "/admin" : "/");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	async function handleRegister(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			const response = await register({
				tenantName: form.get("tenantName"),
				tenantSlug: form.get("tenantSlug"),
				ownerEmail: form.get("ownerEmail"),
				ownerPassword: form.get("ownerPassword"),
			});
			setRegistered(response);
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	async function handleResend() {
		setResendNotice("");
		try {
			await api.resendVerification({ tenantSlug: registered.tenantSlug, email: registered.email });
			setResendNotice("Te reenviamos el mail.");
		} catch (err) {
			setError(err.message);
		}
	}

	if (registered) {
		return (
			<div className="auth-page">
				<div className="auth-card">
					<h1>Revisá tu mail</h1>
					<p className="muted">
						Te mandamos un link de confirmación a <strong>{registered.email}</strong>. Confirmalo para poder
						entrar — el negocio ya está creado, solo falta ese paso.
					</p>
					{resendNotice && <p className="notice">{resendNotice}</p>}
					{error && <p className="error">{error}</p>}
					<button type="button" onClick={handleResend}>
						Reenviar mail
					</button>
				</div>
			</div>
		);
	}

	return (
		<div className="auth-page">
			<div className="auth-card">
				<h1>booking-saas</h1>
				<div className="tabs">
					<button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
						Ingresar
					</button>
					<button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>
						Crear negocio
					</button>
				</div>
				{error && <p className="error">{error}</p>}
				{mode === "login" ? (
					<form onSubmit={handleLogin}>
						<label>
							Identificador del negocio
							<input name="tenantSlug" required />
						</label>
						<label>
							Email
							<input name="email" type="email" required />
						</label>
						<label>
							Contraseña
							<input name="password" type="password" required />
						</label>
						<button type="submit" disabled={loading}>
							{loading ? "Ingresando..." : "Ingresar"}
						</button>
					</form>
				) : (
					<form onSubmit={handleRegister}>
						<label>
							Nombre del negocio
							<input name="tenantName" required />
						</label>
						<label>
							Identificador del negocio (minúsculas, sin espacios)
							<input name="tenantSlug" required pattern="[a-z0-9](-?[a-z0-9])*" />
						</label>
						<label>
							Email del dueño
							<input name="ownerEmail" type="email" required />
						</label>
						<label>
							Contraseña (mín. 8 caracteres)
							<input name="ownerPassword" type="password" minLength={8} required />
						</label>
						<button type="submit" disabled={loading}>
							{loading ? "Creando..." : "Crear negocio"}
						</button>
					</form>
				)}
			</div>
		</div>
	);
}
