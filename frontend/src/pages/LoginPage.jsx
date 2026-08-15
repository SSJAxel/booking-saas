import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import RegisterForm from "../components/RegisterForm.jsx";

export default function LoginPage() {
	const [mode, setMode] = useState("login");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);
	const { login } = useAuth();
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
				{mode === "login" ? (
					<>
						{error && <p className="error">{error}</p>}
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
							<p className="muted" style={{ marginTop: "0.8rem" }}>
								<Link to="/olvide-password">¿Olvidaste tu contraseña?</Link>
							</p>
						</form>
					</>
				) : (
					<>
						<RegisterForm />
						<p className="muted" style={{ marginTop: "1rem" }}>
							<Link to="/precios">Ver planes y precios</Link>
						</p>
					</>
				)}
			</div>
		</div>
	);
}
