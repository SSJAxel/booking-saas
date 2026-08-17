import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { LockIcon } from "../components/icons.jsx";

export default function ResetPasswordPage() {
	const [searchParams] = useSearchParams();
	const { resetPassword } = useAuth();
	const navigate = useNavigate();
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(false);

	const tenantSlug = searchParams.get("tenant");
	const token = searchParams.get("token");
	const linkIncomplete = !tenantSlug || !token;

	async function handleSubmit(event) {
		event.preventDefault();
		setError("");
		const form = new FormData(event.target);
		const newPassword = form.get("newPassword");
		const confirmPassword = form.get("confirmPassword");
		if (newPassword !== confirmPassword) {
			setError("Las contraseñas no coinciden.");
			return;
		}
		setLoading(true);
		try {
			await resetPassword({ tenantSlug, token, newPassword });
			navigate("/panel", { replace: true });
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="auth-page">
			<div className="auth-card">
				<h1>Restablecer contraseña</h1>
				{linkIncomplete ? (
					<>
						<p className="error">Este link está incompleto.</p>
						<p className="muted">
							Pedí uno nuevo desde <Link to="/olvide-password">acá</Link>.
						</p>
					</>
				) : (
					<>
						{error && (
							<>
								<p className="error">{error}</p>
								<p className="muted">
									Si el link venció, pedí uno nuevo desde <Link to="/olvide-password">acá</Link>.
								</p>
							</>
						)}
						<form onSubmit={handleSubmit}>
							<label>
								Nueva contraseña
								<span className="input-icon-wrap">
									<LockIcon width="16" height="16" />
									<input name="newPassword" type="password" required minLength={8} />
								</span>
							</label>
							<label>
								Confirmar contraseña
								<span className="input-icon-wrap">
									<LockIcon width="16" height="16" />
									<input name="confirmPassword" type="password" required minLength={8} />
								</span>
							</label>
							<button type="submit" disabled={loading}>
								{loading ? "Guardando..." : "Guardar nueva contraseña"}
							</button>
						</form>
					</>
				)}
			</div>
		</div>
	);
}
