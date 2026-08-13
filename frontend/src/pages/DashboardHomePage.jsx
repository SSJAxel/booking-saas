import { useEffect, useState } from "react";
import { api } from "../api.js";
import BarChart from "../components/BarChart.jsx";
import StatTile from "../components/StatTile.jsx";

const WEEKDAY_FORMAT = new Intl.DateTimeFormat("es-AR", { weekday: "short" });
const TODAY_FORMAT = new Intl.DateTimeFormat("es-AR", { weekday: "long", day: "numeric", month: "long" });

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
	const [error, setError] = useState("");

	useEffect(() => {
		Promise.all([api.reports.today(), api.reports.traffic(7), api.reports.productSales(7)])
			.then(([t, tr, s]) => {
				setToday(t);
				setTraffic(tr);
				setSales(s);
			})
			.catch((err) => setError(err.message));
	}, []);

	if (error) return <p className="error">{error}</p>;
	if (!today || !traffic || !sales) return <DashboardSkeleton />;

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
		</div>
	);
}
