import { useState } from "react";

const DAYS = [
	["MONDAY", "Lunes"],
	["TUESDAY", "Martes"],
	["WEDNESDAY", "Miércoles"],
	["THURSDAY", "Jueves"],
	["FRIDAY", "Viernes"],
	["SATURDAY", "Sábado"],
	["SUNDAY", "Domingo"],
];

const DEFAULT_START = "09:00";
const DEFAULT_END = "18:00";
const DEFAULT_BREAK_START = "13:00";
const DEFAULT_BREAK_END = "14:00";

function emptyDay() {
	return {
		enabled: false,
		start: DEFAULT_START,
		end: DEFAULT_END,
		hasBreak: false,
		breakStart: DEFAULT_BREAK_START,
		breakEnd: DEFAULT_BREAK_END,
	};
}

/** Turns the flat list of (dayOfWeek, startTime, endTime) rows the API stores into one row per
 * weekday for the table — a day with two stored ranges (rest split around a lunch break) collapses
 * into a single toggled-on day with hasBreak=true, since that's how a user thinks about it. */
function buildDraft(entries) {
	const byDay = {};
	for (const [day] of DAYS) byDay[day] = [];
	for (const e of entries) {
		byDay[e.dayOfWeek]?.push({ id: e.id, start: e.startTime.slice(0, 5), end: e.endTime.slice(0, 5) });
	}
	const draft = {};
	const idsByDay = {};
	for (const [day] of DAYS) {
		const ranges = [...byDay[day]].sort((a, b) => a.start.localeCompare(b.start));
		idsByDay[day] = ranges.map((r) => r.id);
		if (ranges.length === 0) {
			draft[day] = emptyDay();
		} else if (ranges.length === 1) {
			draft[day] = { ...emptyDay(), enabled: true, start: ranges[0].start, end: ranges[0].end };
		} else {
			const [morning, afternoon] = ranges;
			draft[day] = {
				enabled: true,
				start: morning.start,
				end: afternoon.end,
				hasBreak: true,
				breakStart: morning.end,
				breakEnd: afternoon.start,
			};
		}
	}
	return { draft, idsByDay };
}

/** The actual (start,end) ranges a day resolves to once saved — one range normally, two if split
 * around a break. Source of truth for both the diff-against-baseline check and what gets sent. */
function rangesFor(day) {
	if (!day.enabled) return [];
	if (!day.hasBreak) return [{ start: day.start, end: day.end }];
	return [
		{ start: day.start, end: day.breakStart },
		{ start: day.breakEnd, end: day.end },
	];
}

function isDayValid(day) {
	if (!day.enabled) return true;
	if (day.start >= day.end) return false;
	if (day.hasBreak && !(day.start < day.breakStart && day.breakStart < day.breakEnd && day.breakEnd < day.end)) {
		return false;
	}
	return true;
}

/**
 * One weekly recurring schedule editor, shared by branch hours and professional availability —
 * both are the same shape server-side (dayOfWeek + startTime + endTime rows, multiple allowed per
 * day). `onCreate`/`onDelete` are the two API calls that already exist for either resource; this
 * component never needs a bulk/update endpoint — a changed day is just delete-its-old-rows then
 * create-its-new-ones, reconciled locally.
 */
export default function WeeklySchedule({ entries, onCreate, onDelete, onSaved, onError }) {
	const initial = useState(() => buildDraft(entries))[0];
	const [draft, setDraft] = useState(initial.draft);
	const [baseline, setBaseline] = useState(initial.draft);
	const [idsByDay, setIdsByDay] = useState(initial.idsByDay);
	const [saving, setSaving] = useState(false);

	const isDirty = JSON.stringify(draft) !== JSON.stringify(baseline);
	const isValid = DAYS.every(([day]) => isDayValid(draft[day]));

	function updateDay(day, patch) {
		setDraft((d) => ({ ...d, [day]: { ...d[day], ...patch } }));
	}

	function copyToAll(day) {
		const source = draft[day];
		setDraft((d) => {
			const next = { ...d };
			for (const [key] of DAYS) next[key] = { ...source };
			return next;
		});
	}

	function discard() {
		setDraft(baseline);
	}

	async function handleSave() {
		if (!isValid) return;
		onError?.("");
		setSaving(true);
		try {
			const nextIdsByDay = { ...idsByDay };
			for (const [day] of DAYS) {
				const desired = rangesFor(draft[day]);
				const before = rangesFor(baseline[day]);
				if (JSON.stringify(desired) === JSON.stringify(before)) continue;

				for (const id of idsByDay[day]) {
					await onDelete(id);
				}
				const created = [];
				for (const r of desired) {
					const response = await onCreate({ dayOfWeek: day, startTime: `${r.start}:00`, endTime: `${r.end}:00` });
					created.push(response.id);
				}
				nextIdsByDay[day] = created;
			}
			setIdsByDay(nextIdsByDay);
			setBaseline(draft);
			onSaved?.();
		} catch (err) {
			onError?.(err.message);
		} finally {
			setSaving(false);
		}
	}

	return (
		<div className="schedule-editor">
			<table className="schedule-table">
				<thead>
					<tr>
						<th>Día</th>
						<th>Estado</th>
						<th>Inicio</th>
						<th>Fin</th>
						<th>Descanso</th>
						<th />
					</tr>
				</thead>
				<tbody>
					{DAYS.map(([day, label], index) => {
						const d = draft[day];
						return (
							<tr key={day}>
								<td>{label}</td>
								<td>
									<label className="switch">
										<input
											type="checkbox"
											checked={d.enabled}
											onChange={(e) => updateDay(day, { enabled: e.target.checked })}
										/>
										<span className="track" />
										<span className="thumb" />
									</label>
								</td>
								{d.enabled ? (
									<>
										<td>
											<input
												type="time"
												aria-label={`${label}: inicio`}
												value={d.start}
												onChange={(e) => updateDay(day, { start: e.target.value })}
											/>
										</td>
										<td>
											<input
												type="time"
												aria-label={`${label}: fin`}
												value={d.end}
												onChange={(e) => updateDay(day, { end: e.target.value })}
											/>
										</td>
										<td>
											{d.hasBreak ? (
												<div className="break-row">
													<input
														type="time"
														aria-label={`${label}: descanso desde`}
														value={d.breakStart}
														onChange={(e) => updateDay(day, { breakStart: e.target.value })}
													/>
													<span className="muted">–</span>
													<input
														type="time"
														aria-label={`${label}: descanso hasta`}
														value={d.breakEnd}
														onChange={(e) => updateDay(day, { breakEnd: e.target.value })}
													/>
													<button
														type="button"
														className="link-button"
														aria-label={`Quitar descanso del ${label}`}
														onClick={() => updateDay(day, { hasBreak: false })}
													>
														×
													</button>
												</div>
											) : (
												<button type="button" className="link-button" onClick={() => updateDay(day, { hasBreak: true })}>
													+ Descanso
												</button>
											)}
										</td>
									</>
								) : (
									<td colSpan={3} className="muted">
										Cerrado
									</td>
								)}
								<td>
									{index === 0 && (
										<button type="button" className="link-button" onClick={() => copyToAll(day)}>
											Copiar en todos
										</button>
									)}
								</td>
							</tr>
						);
					})}
				</tbody>
			</table>

			{!isValid && <p className="error">Revisá los horarios: el inicio debe ser antes del fin (y el descanso, estar adentro).</p>}

			{isDirty && (
				<div className="button-row" style={{ marginTop: "0.8rem" }}>
					<button type="button" disabled={saving || !isValid} onClick={handleSave}>
						{saving ? "Guardando..." : "Guardar horario"}
					</button>
					<button type="button" className="secondary" disabled={saving} onClick={discard}>
						Descartar cambios
					</button>
				</div>
			)}
		</div>
	);
}
