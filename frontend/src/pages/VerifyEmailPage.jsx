import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";

export default function VerifyEmailPage() {
	const [searchParams] = useSearchParams();
	const { verifyEmail } = useAuth();
	const navigate = useNavigate();
	const [status, setStatus] = useState("checking");
	const [error, setError] = useState("");

	const tenantSlug = searchParams.get("tenant");
	const token = searchParams.get("token");

	useEffect(() => {
		if (!tenantSlug || !token) {
			setStatus("error");
			setError("Este link de verificación está incompleto.");
			return;
		}
		verifyEmail({ tenantSlug, token })
			.then(() => {
				setStatus("done");
				navigate("/", { replace: true });
			})
			.catch((err) => {
				setStatus("error");
				setError(err.message);
			});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [tenantSlug, token]);

	return (
		<div className="auth-page">
			<div className="auth-card">
				<h1>Verificando email</h1>
				{status === "checking" && <p className="muted">Un momento...</p>}
				{status === "error" && (
					<>
						<p className="error">{error}</p>
						<p className="muted">
							Si el link venció, podés pedir uno nuevo desde <Link to="/login">la pantalla de ingreso</Link>.
						</p>
					</>
				)}
			</div>
		</div>
	);
}
