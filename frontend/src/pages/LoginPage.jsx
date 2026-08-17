import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import RegisterForm from "../components/RegisterForm.jsx";
import { CalendarIcon, MailIcon, LockIcon, StoreIcon } from "../components/icons.jsx";

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
			navigate(res.platformAdmin ? "/admin" : "/panel");
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="auth-page">
			<div className="auth-card">
				<div className="auth-brand-mark">
					<CalendarIcon width="24" height="24" />
				</div>
				<h1>CapiBooking</h1>
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
								<span className="input-icon-wrap">
									<StoreIcon width="16" height="16" />
									<input name="tenantSlug" required />
								</span>
							</label>
							<label>
								Email
								<span className="input-icon-wrap">
									<MailIcon width="16" height="16" />
									<input name="email" type="email" required />
								</span>
							</label>
							<label>
								Contraseña
								<span className="input-icon-wrap">
									<LockIcon width="16" height="16" />
									<input name="password" type="password" required />
								</span>
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
							<Link to="/">Ver planes y precios</Link>
						</p>
					</>
				)}
			</div>
		</div>
	);
}
