const PX_PER_HOUR = 56;
// Tall enough for time+client on one line plus the professional name on a second — a 30min
// appointment only computes to 28px from duration alone, not enough room for that second line.
const MIN_BLOCK_HEIGHT = 34;
const WEEKDAY_SHORT = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

function pad(n) {
	return String(n).padStart(2, "0");
}

function toDateKey(date) {
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function minutesOfDay(date) {
	return date.getHours() * 60 + date.getMinutes();
}

/**
 * Side-by-side layout for appointments that overlap the same day (different professionals booked
 * at the same time) — standard greedy calendar-event packing: walk appointments in start-time
 * order, put each one in the first column whose last appointment already ended, and once a group
 * of mutually-overlapping appointments closes, give every appointment in it the same column count
 * so they split the width evenly instead of drawing on top of each other.
 */
function layoutDay(appointments) {
	const sorted = [...appointments].sort((a, b) => a.startTime.localeCompare(b.startTime));
	const positioned = [];
	let columnEnds = [];
	let clusterIndices = [];
	let clusterEnd = null;

	function closeCluster() {
		if (clusterIndices.length === 0) return;
		const totalCols = Math.max(...clusterIndices.map((i) => positioned[i].col)) + 1;
		for (const i of clusterIndices) positioned[i].totalCols = totalCols;
	}

	for (const appointment of sorted) {
		const start = new Date(appointment.startTime).getTime();
		const end = new Date(appointment.endTime).getTime();
		if (clusterEnd !== null && start >= clusterEnd) {
			closeCluster();
			clusterIndices = [];
			columnEnds = [];
			clusterEnd = null;
		}
		let col = columnEnds.findIndex((endTime) => endTime <= start);
		if (col === -1) {
			col = columnEnds.length;
			columnEnds.push(end);
		} else {
			columnEnds[col] = end;
		}
		positioned.push({ appointment, col, totalCols: 1 });
		clusterIndices.push(positioned.length - 1);
		clusterEnd = clusterEnd === null ? end : Math.max(clusterEnd, end);
	}
	closeCluster();
	return positioned;
}

/**
 * A real week calendar grid (X = day, Y = hour). `hourStart`/`hourEnd` are the tenant's actual
 * opening-to-closing hours (widest span across the week's professionals — see
 * AppointmentsPage.computeBusinessHourBounds), not derived from what happens to be booked, so the
 * grid is a fixed, predictable size every week instead of jumping around depending on the day.
 *
 * Hour lines are real elements (not a CSS background-image), and every hour label is top-aligned
 * to its line instead of vertically centered on it — centering the very first label would put half
 * of it above y=0, where the scroll container has nothing to show, so it was rendering clipped off.
 */
export default function WeekCalendar({ days, appointmentsByDay, onSelect, hourStart, hourEnd, professionalName }) {
	const hours = Array.from({ length: hourEnd - hourStart }, (_, i) => hourStart + i);
	const bodyHeight = (hourEnd - hourStart) * PX_PER_HOUR;
	const today = toDateKey(new Date());

	return (
		<div className="time-grid">
			<div className="time-grid-header">
				<div className="time-grid-gutter" />
				{days.map((day) => (
					<div key={toDateKey(day)} className={`time-grid-day-header${toDateKey(day) === today ? " is-today" : ""}`}>
						<span className="time-grid-day-name">{WEEKDAY_SHORT[(day.getDay() + 6) % 7]}</span>
						<span className="time-grid-day-num">{day.getDate()}</span>
					</div>
				))}
			</div>
			<div className="time-grid-body" style={{ height: bodyHeight }}>
				<div className="time-grid-gutter">
					{hours.map((h) => (
						<span key={h} className="time-grid-hour-label" style={{ top: (h - hourStart) * PX_PER_HOUR }}>
							{h}:00
						</span>
					))}
				</div>
				{days.map((day) => {
					const key = toDateKey(day);
					const positioned = layoutDay(appointmentsByDay[key] ?? []);
					return (
						<div key={key} className="time-grid-day-col">
							{hours.map((h) => (
								<div key={h} className="time-grid-hour-line" style={{ top: (h - hourStart) * PX_PER_HOUR }} />
							))}
							{positioned.map(({ appointment, col, totalCols }) => {
								const start = new Date(appointment.startTime);
								const top = (minutesOfDay(start) - hourStart * 60) * (PX_PER_HOUR / 60);
								const durationMin = (new Date(appointment.endTime) - start) / 60000;
								const height = Math.max(durationMin * (PX_PER_HOUR / 60), MIN_BLOCK_HEIGHT);
								const width = 100 / totalCols;
								return (
									<button
										type="button"
										key={appointment.id}
										className={`appt-block status-${appointment.status.toLowerCase()}`}
										style={{ top, height, width: `calc(${width}% - 4px)`, left: `calc(${width * col}% + 2px)` }}
										onClick={() => onSelect(appointment)}
										title={`${pad(start.getHours())}:${pad(start.getMinutes())} · ${appointment.clientName} · ${professionalName(appointment.professionalId)}`}
									>
										<span className="appt-block-line1">
											<span className="appt-block-time">
												{pad(start.getHours())}:{pad(start.getMinutes())}
											</span>{" "}
											<span className="appt-block-name">{appointment.clientName}</span>
										</span>
										<span className="appt-block-professional">{professionalName(appointment.professionalId)}</span>
									</button>
								);
							})}
						</div>
					);
				})}
			</div>
		</div>
	);
}

export { toDateKey };
