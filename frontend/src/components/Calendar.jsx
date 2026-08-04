import { useState } from "react";

const WEEKDAYS = ["Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do"];
const MONTHS = [
	"Enero",
	"Febrero",
	"Marzo",
	"Abril",
	"Mayo",
	"Junio",
	"Julio",
	"Agosto",
	"Septiembre",
	"Octubre",
	"Noviembre",
	"Diciembre",
];

function toDateKey(date) {
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function startOfDay(date) {
	const d = new Date(date);
	d.setHours(0, 0, 0, 0);
	return d;
}

/** Plain month-grid date picker — no date library, just Date arithmetic. Selection is a
 * "yyyy-MM-dd" string (matches what the availability/book API expects as LocalDate). */
export default function Calendar({ selected, onSelect }) {
	const today = startOfDay(new Date());
	const [viewDate, setViewDate] = useState(new Date(today.getFullYear(), today.getMonth(), 1));

	const year = viewDate.getFullYear();
	const month = viewDate.getMonth();
	const firstOfMonth = new Date(year, month, 1);
	const leadingBlanks = (firstOfMonth.getDay() + 6) % 7; // Monday-first grid
	const daysInMonth = new Date(year, month + 1, 0).getDate();

	const cells = [];
	for (let i = 0; i < leadingBlanks; i++) cells.push(null);
	for (let day = 1; day <= daysInMonth; day++) cells.push(new Date(year, month, day));

	const canGoBack = new Date(year, month, 1) > new Date(today.getFullYear(), today.getMonth(), 1);

	return (
		<div className="calendar">
			<div className="calendar-header">
				<button
					type="button"
					className="secondary"
					onClick={() => setViewDate(new Date(year, month - 1, 1))}
					disabled={!canGoBack}
					aria-label="Mes anterior"
				>
					‹
				</button>
				<span className="calendar-title">
					{MONTHS[month]} {year}
				</span>
				<button
					type="button"
					className="secondary"
					onClick={() => setViewDate(new Date(year, month + 1, 1))}
					aria-label="Mes siguiente"
				>
					›
				</button>
			</div>
			<div className="calendar-grid calendar-weekdays">
				{WEEKDAYS.map((w) => (
					<span key={w}>{w}</span>
				))}
			</div>
			<div className="calendar-grid">
				{cells.map((date, i) => {
					if (!date) return <span key={`blank-${i}`} />;
					const key = toDateKey(date);
					const isPast = date < today;
					const isSelected = selected === key;
					return (
						<button
							type="button"
							key={key}
							className={`calendar-day${isSelected ? " calendar-day-selected" : ""}`}
							disabled={isPast}
							onClick={() => onSelect(key)}
						>
							{date.getDate()}
						</button>
					);
				})}
			</div>
		</div>
	);
}
