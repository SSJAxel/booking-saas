import { useEffect, useState } from "react";
import { api } from "../api.js";
import { useAuth } from "../auth/AuthContext.jsx";
import WelcomeBanner from "../components/WelcomeBanner.jsx";

const STATUSES = ["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW"];
const NEXT_STATUS = {
	PENDING: ["CONFIRMED", "CANCELLED"],
	CONFIRMED: ["CANCELLED", "COMPLETED", "NO_SHOW"],
	CANCELLED: [],
	COMPLETED: [],
	NO_SHOW: [],
};

export default function AppointmentsPage() {
	const { session } = useAuth();
	const [appointments, setAppointments] = useState([]);
	const [statusFilter, setStatusFilter] = useState("");
	const [error, setError] = useState("");
	const [loading, setLoading] = useState(true);
	const [showWelcome, setShowWelcome] = useState(false);

	useEffect(() => {
		const dismissed = localStorage.getItem(`onboarding-dismissed-${session.tenantSlug}`) === "true";
		if (dismissed) return;
		api.branches
			.list()
			.then((branches) => setShowWelcome(branches.length === 0))
			.catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function refresh() {
		setLoading(true);
		try {
			setAppointments(await api.appointments.list({ status: statusFilter || undefined }));
		} catch (err) {
			setError(err.message);
		} finally {
			setLoading(false);
		}
	}

	useEffect(() => {
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [statusFilter]);

	async function handleTransition(id, status) {
		setError("");
		try {
			await api.appointments.transition(id, status);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	async function handleConfirmDeposit(id) {
		setError("");
		try {
			await api.appointments.confirmDeposit(id);
			refresh();
		} catch (err) {
			setError(err.message);
		}
	}

	return (
		<div>
			<h1>Turnos</h1>
			{showWelcome && <WelcomeBanner onDismiss={() => setShowWelcome(false)} />}
			{error && <p className="error">{error}</p>}
			<div className="filters">
				<label>
					Estado
					<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
						<option value="">Todos</option>
						{STATUSES.map((s) => (
							<option key={s} value={s}>
								{s}
							</option>
						))}
					</select>
				</label>
			</div>
			{loading ? (
				<p>Cargando...</p>
			) : appointments.length === 0 ? (
				<p className="muted">No hay turnos para este filtro.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th>Cliente</th>
							<th>Inicio</th>
							<th>Estado</th>
							<th>Pago</th>
							<th>Acciones</th>
						</tr>
					</thead>
					<tbody>
						{appointments.map((a) => (
							<tr key={a.id}>
								<td>
									{a.clientName}
									<br />
									<span className="muted">{a.clientEmail}</span>
								</td>
								<td>{new Date(a.startTime).toLocaleString()}</td>
								<td>
									<span className={`badge badge-${a.status.toLowerCase()}`}>{a.status}</span>
								</td>
								<td>{a.paymentStatus}</td>
								<td>
									{a.paymentStatus === "PENDING" && (
										<button
											type="button"
											className="link-button"
											onClick={() => handleConfirmDeposit(a.id)}
											title="Marcar la seña como recibida (transferencia por alias) y confirmar el turno"
										>
											Confirmar seña
										</button>
									)}
									{NEXT_STATUS[a.status].map((s) => (
										<button key={s} type="button" className="link-button" onClick={() => handleTransition(a.id, s)}>
											{s}
										</button>
									))}
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}
