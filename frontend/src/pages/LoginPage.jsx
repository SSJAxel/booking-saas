import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";

export default function LoginPage() {
	const [mode, setMode] = useState("login");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const { login, register } = useAuth();
	const navigate = useNavigate();

	async function handleLogin(event) {
		event.preventDefault();
		setError("");
		setLoading(true);
		const form = new FormData(event.target);
		try {
			await login({
				tenantSlug: form.get("tenantSlug"),
				email: form.get("email"),
				password: form.get("password"),
			});
			navigate("/");
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
			await register({
				tenantName: form.get("tenantName"),
				tenantSlug: form.get("tenantSlug"),
				ownerEmail: form.get("ownerEmail"),
				ownerPassword: form.get("ownerPassword"),
			});
			navigate("/");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
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
							Slug del negocio
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
							Slug (minúsculas, sin espacios)
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
