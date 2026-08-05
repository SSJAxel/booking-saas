import { useState } from "react";

const WEEKDAY_LABELS = ["Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do"];
const MONTH_LABEL_FORMAT = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" });

function pad(n) {
	return String(n).padStart(2, "0");
}

// Built from year/month/day integers, never from Date#toISOString() — that converts to UTC and
// can shift the date by a day depending on the browser's local offset, which would silently
// block the wrong day.
function dateKey(year, month, day) {
	return `${year}-${pad(month + 1)}-${pad(day)}`;
}

function todayKey() {
	const now = new Date();
	return dateKey(now.getFullYear(), now.getMonth(), now.getDate());
}

function capitalize(text) {
	return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Multi-select month calendar for whole-day time-off blocks (vacations, sick leave) — click
 * toggles a day on/off, selection doesn't need to be contiguous. The existing single-day form
 * (with optional hours) stays separate for the "a few hours on one day" case. */
export default function MonthCalendar({ selected, onToggle }) {
	const [cursor, setCursor] = useState(() => {
		const now = new Date();
		return { year: now.getFullYear(), month: now.getMonth() };
	});

	const firstOfMonth = new Date(cursor.year, cursor.month, 1);
	// Date#getDay(): 0=Sunday..6=Saturday — shift so the grid starts on Monday, matching WEEKDAY_LABELS.
	const leadingBlanks = (firstOfMonth.getDay() + 6) % 7;
	const daysInMonth = new Date(cursor.year, cursor.month + 1, 0).getDate();
	const today = todayKey();

	function changeMonth(delta) {
		setCursor(({ year, month }) => {
			const next = new Date(year, month + delta, 1);
			return { year: next.getFullYear(), month: next.getMonth() };
		});
	}

	const cells = Array.from({ length: leadingBlanks }, () => null).concat(
		Array.from({ length: daysInMonth }, (_, i) => i + 1),
	);

	return (
		<div className="month-calendar">
			<div className="month-calendar-header">
				<button type="button" className="secondary" onClick={() => changeMonth(-1)} aria-label="Mes anterior">
					‹
				</button>
				<span className="month-calendar-title">{capitalize(MONTH_LABEL_FORMAT.format(firstOfMonth))}</span>
				<button type="button" className="secondary" onClick={() => changeMonth(1)} aria-label="Mes siguiente">
					›
				</button>
			</div>
			<div className="month-calendar-grid">
				{WEEKDAY_LABELS.map((label) => (
					<span key={label} className="month-calendar-weekday">
						{label}
					</span>
				))}
				{cells.map((day, i) => {
					if (day === null) return <span key={`blank-${i}`} />;
					const key = dateKey(cursor.year, cursor.month, day);
					const isPast = key < today;
					const classNames = ["month-calendar-day"];
					if (selected.has(key)) classNames.push("selected");
					if (key === today) classNames.push("today");
					return (
						<button
							key={key}
							type="button"
							className={classNames.join(" ")}
							disabled={isPast}
							onClick={() => onToggle(key)}
						>
							{day}
						</button>
					);
				})}
			</div>
		</div>
	);
}
