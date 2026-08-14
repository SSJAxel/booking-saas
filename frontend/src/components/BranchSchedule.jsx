import { useState } from "react";

const ORDERED_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const DAY_LABELS = {
	MONDAY: "Lunes",
	TUESDAY: "Martes",
	WEDNESDAY: "Miércoles",
	THURSDAY: "Jueves",
	FRIDAY: "Viernes",
	SATURDAY: "Sábado",
	SUNDAY: "Domingo",
};

const ClockIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<circle cx="12" cy="12" r="9" />
		<path d="M12 7v5l3 3" />
	</svg>
);

function timeToMinutes(t) {
	const [h, m] = t.split(":").map(Number);
	return h * 60 + m;
}

function formatRange(h) {
	return `${h.startTime.slice(0, 5)}-${h.endTime.slice(0, 5)}`;
}

function groupByDay(hours) {
	const byDay = Object.fromEntries(ORDERED_DAYS.map((d) => [d, []]));
	for (const h of hours) byDay[h.dayOfWeek]?.push(h);
	for (const d of ORDERED_DAYS) byDay[d].sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime));
	return byDay;
}

/** Derives "open now" / "next opening" purely from the browser's local clock — the public API
 * doesn't expose the tenant's timezone yet (see BookingPage.jsx's gap notes), so this assumes the
 * visitor and the business share one, true for this project's current market. Checks today's
 * remaining ranges first (a range that hasn't started yet today still counts as "next"), then walks
 * forward up to a full week for the earliest ranges that opens. */
function computeStatus(byDay, todayIdx, nowMin) {
	const todayRanges = byDay[ORDERED_DAYS[todayIdx]];
	const openRange = todayRanges.find((r) => nowMin >= timeToMinutes(r.startTime) && nowMin < timeToMinutes(r.endTime));
	if (openRange) return { open: true, dayIdx: todayIdx, range: openRange };

	for (let offset = 0; offset < 8; offset++) {
		const dayIdx = (todayIdx + offset) % 7;
		for (const r of byDay[ORDERED_DAYS[dayIdx]]) {
			if (offset === 0 && timeToMinutes(r.startTime) <= nowMin) continue;
			return { open: false, dayIdx, range: r };
		}
	}
	return { open: false, dayIdx: null, range: null };
}

/** Sidebar schedule widget: a one-line "Abierto/Cerrado" status the user asked for (today's
 * range if open, otherwise the next day+range the branch opens), with a chevron that expands into
 * the full week — today's row highlighted in the tenant's accent color regardless of whether it's
 * currently inside or outside that day's hours, so "today" stays visually anchored even when
 * closed right now. */
export default function BranchSchedule({ hours }) {
	const [expanded, setExpanded] = useState(false);
	if (!hours || hours.length === 0) return null;

	const byDay = groupByDay(hours);
	const now = new Date();
	const todayIdx = (now.getDay() + 6) % 7; // Monday = 0
	const nowMin = now.getHours() * 60 + now.getMinutes();
	const status = computeStatus(byDay, todayIdx, nowMin);

	return (
		<div className="pb-branch-info-row pb-schedule">
			<ClockIcon />
			<div className="pb-schedule-body">
				<button type="button" className="pb-schedule-toggle" onClick={() => setExpanded((v) => !v)}>
					<span className={status.open ? "pb-schedule-open" : "pb-schedule-closed"}>
						{status.open
							? `Abierto · ${DAY_LABELS[ORDERED_DAYS[status.dayIdx]]} ${formatRange(status.range)}`
							: status.range
								? `Cerrado · Abre ${DAY_LABELS[ORDERED_DAYS[status.dayIdx]]} ${formatRange(status.range)}`
								: "Cerrado"}
					</span>
					<svg
						className={`pb-schedule-chevron${expanded ? " open" : ""}`}
						width="13"
						height="13"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						strokeWidth="2.5"
					>
						<path d="M6 9l6 6 6-6" />
					</svg>
				</button>
				{expanded && (
					<ul className="pb-schedule-week">
						{ORDERED_DAYS.map((day, idx) => (
							<li key={day} className={idx === todayIdx ? "pb-schedule-today" : undefined}>
								<span>{DAY_LABELS[day]}</span>
								<span>{byDay[day].length > 0 ? byDay[day].map(formatRange).join(", ") : "Cerrado"}</span>
							</li>
						))}
					</ul>
				)}
			</div>
		</div>
	);
}
