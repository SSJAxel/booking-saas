import { useEffect, useState } from "react";
import { api } from "../api.js";
import BarChart from "../components/BarChart.jsx";
import StatTile from "../components/StatTile.jsx";
import { planHasCommissions } from "../planLimits.js";

const WEEKDAY_FORMAT = new Intl.DateTimeFormat("es-AR", { weekday: "short" });
const TODAY_FORMAT = new Intl.DateTimeFormat("es-AR", { weekday: "long", day: "numeric", month: "long" });

function todayIsoDate() {
	return new Date().toISOString().slice(0, 10);
}

function firstOfMonthIsoDate() {
	const d = new Date();
	return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

// "to" is exclusive on the backend (Instant), so a date picked as the last day of the range needs
// to become the start of the *next* day to actually include everything on that day.
function dateRangeToInstants(fromIsoDate, toIsoDate) {
	const from = fromIsoDate ? new Date(fromIsoDate + "T00:00:00").toISOString() : undefined;
	let to;
	if (toIsoDate) {
		const toDate = new Date(toIsoDate + "T00:00:00");
		toDate.setDate(toDate.getDate() + 1);
		to = toDate.toISOString();
	}
	return { from, to };
}

function dayLabel(isoDate) {
	// new Date("YYYY-MM-DD") parses as UTC midnight — fine here since we only read the
	// weekday, not the exact instant, and the date string is already in the tenant's zone.
	const label = WEEKDAY_FORMAT.format(new Date(isoDate + "T00:00:00"));
	return label.charAt(0).toUpperCase() + label.slice(1, 3);
}

function capitalize(text) {
	return text.charAt(0).toUpperCase() + text.slice(1);
}

function CheckIcon() {
	return (
		<svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path d="m5 13 4 4L19 7" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

function ClockIcon() {
	return (
		<svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
			<path d="M12 7v5l3.5 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

function XIcon() {
	return (
		<svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
		</svg>
	);
}

function ChartCard({ title, total, data, color, valueFormatter, emptyText }) {
	const isEmpty = data.every((d) => d.value === 0);
	return (
		<div className="card">
			<div className="chart-card-header">
				<span className="chart-card-title">{title}</span>
				<span className="chart-card-total" style={{ color }}>
					{valueFormatter ? valueFormatter(total) : total}
				</span>
			</div>
			{isEmpty ? (
				<p className="muted chart-empty">{emptyText}</p>
			) : (
				<BarChart data={data} color={color} valueFormatter={valueFormatter} />
			)}
		</div>
	);
}

function DashboardSkeleton() {
	return (
		<div>
			<h1>Inicio</h1>
			<div className="cards">
				<div className="card skeleton-block" style={{ height: "104px" }} />
				<div className="card skeleton-block" style={{ height: "104px" }} />
				<div className="card skeleton-block" style={{ height: "104px" }} />
			</div>
			<div className="card skeleton-block" style={{ height: "180px", marginTop: "1.6rem" }} />
			<div className="card skeleton-block" style={{ height: "180px", marginTop: "1.6rem" }} />
		</div>
	);
}

export default function DashboardHomePage() {
	const [today, setToday] = useState(null);
	const [traffic, setTraffic] = useState(null);
	const [sales, setSales] = useState(null);
	const [tenant, setTenant] = useState(null);
	const [error, setError] = useState("");

	useEffect(() => {
		Promise.all([api.reports.today(), api.reports.traffic(7), api.reports.productSales(7), api.tenant.get()])
			.then(([t, tr, s, tn]) => {
				setToday(t);
				setTraffic(tr);
				setSales(s);
				setTenant(tn);
			})
			.catch((err) => setError(err.message));
	}, []);

	if (error) return <p className="error">{error}</p>;
	if (!today || !traffic || !sales || !tenant) return <DashboardSkeleton />;

	const confirmed = today.byStatus.CONFIRMED ?? 0;
	const pending = today.byStatus.PENDING ?? 0;
	const cancelled = today.byStatus.CANCELLED ?? 0;

	const trafficData = traffic.map((d) => ({ label: dayLabel(d.date), value: d.count }));
	const salesData = sales.map((d) => ({ label: dayLabel(d.date), value: Number(d.totalAmount) }));
	const trafficTotal = trafficData.reduce((sum, d) => sum + d.value, 0);
	const salesTotal = salesData.reduce((sum, d) => sum + d.value, 0);

	return (
		<div>
			<h1>Inicio</h1>
			<p className="muted dashboard-date">{capitalize(TODAY_FORMAT.format(new Date()))}</p>

			<p className="label">Turnos de hoy</p>
			<div className="cards">
				<StatTile
					icon={<CheckIcon />}
					iconBg="var(--ok-bg)"
					iconColor="var(--ok-text)"
					value={confirmed}
					label="Confirmados"
				/>
				<StatTile
					icon={<ClockIcon />}
					iconBg="var(--warning-bg)"
					iconColor="var(--warning)"
					value={pending}
					label="Pendientes"
				/>
				<StatTile icon={<XIcon />} iconBg="var(--danger-bg)" iconColor="var(--danger)" value={cancelled} label="Cancelados" />
			</div>

			<p className="label">Tráfico de clientes</p>
			<ChartCard
				title="Turnos por día (últimos 7 días)"
				total={`${trafficTotal} turnos`}
				data={trafficData}
				color="var(--accent)"
				emptyText="Sin turnos reservados en los últimos 7 días."
			/>

			<p className="label">Ventas de productos</p>
			<ChartCard
				title="Ventas por día (últimos 7 días)"
				total={salesTotal}
				valueFormatter={(v) => `$${v.toLocaleString("es-AR")}`}
				data={salesData}
				color="var(--ok-text)"
				emptyText="Sin ventas de productos en los últimos 7 días."
			/>

			{planHasCommissions(tenant.planTier) && tenant.commissionsEnabled && <CommissionsSection />}
		</div>
	);
}

/** Per-professional commission breakdown for a picked period — only rendered when the tenant's
 * plan supports it *and* they've turned it on (see ProfessionalsPage.jsx's toggle). Separate
 * component so its own date-range state/fetch don't get tangled with the rest of the dashboard's
 * fixed-7-days charts above. */
function CommissionsSection() {
	const [from, setFrom] = useState(firstOfMonthIsoDate());
	const [to, setTo] = useState(todayIsoDate());
	const [rows, setRows] = useState(null);
	const [error, setError] = useState("");

	function refresh() {
		setError("");
		const { from: fromInstant, to: toInstant } = dateRangeToInstants(from, to);
		api.reports
			.commissions(fromInstant, toInstant)
			.then(setRows)
			.catch((err) => setError(err.message));
	}

	useEffect(() => {
		refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	const total = (rows ?? []).reduce((sum, r) => sum + Number(r.totalCommission), 0);

	return (
		<>
			<p className="label">Comisiones</p>
			<div className="card">
				<form
					className="inline-form small"
					onSubmit={(event) => {
						event.preventDefault();
						refresh();
					}}
				>
					<label>
						Desde
						<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
					</label>
					<label>
						Hasta
						<input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
					</label>
					<button type="submit">Ver</button>
					{rows && <span className="muted">Total: ${total.toLocaleString("es-AR")}</span>}
				</form>

				{error && <p className="error">{error}</p>}

				{!rows ? (
					<p className="muted">Cargando...</p>
				) : rows.length === 0 ? (
					<p className="muted">
						Nadie tiene comisión configurada, o nadie generó turnos/ventas en este período. Configurá los % en
						Profesionales.
					</p>
				) : (
					<table>
						<thead>
							<tr>
								<th>Profesional</th>
								<th>Ventas servicios</th>
								<th>Comisión servicios</th>
								<th>Ventas productos</th>
								<th>Comisión productos</th>
								<th>Total</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((r) => (
								<tr key={r.professionalId}>
									<td>{r.professionalName}</td>
									<td>${Number(r.serviceRevenue).toLocaleString("es-AR")}</td>
									<td>${Number(r.serviceCommission).toLocaleString("es-AR")}</td>
									<td>${Number(r.productRevenue).toLocaleString("es-AR")}</td>
									<td>${Number(r.productCommission).toLocaleString("es-AR")}</td>
									<td>
										<strong>${Number(r.totalCommission).toLocaleString("es-AR")}</strong>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				)}
			</div>
		</>
	);
}
