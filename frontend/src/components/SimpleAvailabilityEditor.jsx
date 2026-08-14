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
const DAY_LABELS = Object.fromEntries(DAYS);
const DAY_ORDER = Object.fromEntries(DAYS.map(([day], index) => [day, index]));

/**
 * The other way to build the same weekly schedule as WeeklySchedule.jsx — one slot at a time
 * instead of a 7-day grid shown all at once. For a business fielding more demand than it can
 * handle, opening up availability gradually (one day/slot added when they're ready for it) reads
 * differently than a grid that visually asks "fill in your whole week now". Same underlying data
 * (WeeklyAvailability rows) and the same onCreate/onDelete calls as the grid — a professional can
 * freely switch between this and the grid view, neither one owns the data.
 */
export default function SimpleAvailabilityEditor({ entries, onCreate, onDelete, onSaved, onError }) {
	const [adding, setAdding] = useState(false);

	const sorted = [...entries].sort((a, b) => {
		const dayDiff = DAY_ORDER[a.dayOfWeek] - DAY_ORDER[b.dayOfWeek];
		return dayDiff !== 0 ? dayDiff : a.startTime.localeCompare(b.startTime);
	});

	async function handleDelete(id) {
		onError?.("");
		try {
			await onDelete(id);
			onSaved?.();
		} catch (err) {
			onError?.(err.message);
		}
	}

	async function handleAdd(event) {
		event.preventDefault();
		onError?.("");
		const form = new FormData(event.target);
		const dayOfWeek = form.get("dayOfWeek");
		const startTime = form.get("startTime");
		const endTime = form.get("endTime");
		if (!dayOfWeek || !startTime || !endTime) return;
		if (startTime >= endTime) {
			onError?.("El inicio de la franja debe ser antes del fin.");
			return;
		}
		setAdding(true);
		try {
			await onCreate({ dayOfWeek, startTime: `${startTime}:00`, endTime: `${endTime}:00` });
			event.target.reset();
			onSaved?.();
		} catch (err) {
			onError?.(err.message);
		} finally {
			setAdding(false);
		}
	}

	return (
		<div className="simple-availability">
			<div className="chip-row">
				{sorted.length === 0 && <span className="muted">Sin franjas cargadas todavía — agregá la primera abajo.</span>}
				{sorted.map((e) => (
					<button
						key={e.id}
						type="button"
						className="chip chip-removable"
						aria-label={`Eliminar franja de ${DAY_LABELS[e.dayOfWeek]}`}
						title="Eliminar"
						onClick={() => handleDelete(e.id)}
					>
						{DAY_LABELS[e.dayOfWeek]} {e.startTime.slice(0, 5)}–{e.endTime.slice(0, 5)} ×
					</button>
				))}
			</div>
			<form className="add-form" onSubmit={handleAdd}>
				<select name="dayOfWeek" required defaultValue="">
					<option value="" disabled>
						Día
					</option>
					{DAYS.map(([day, label]) => (
						<option key={day} value={day}>
							{label}
						</option>
					))}
				</select>
				<input name="startTime" type="time" required aria-label="Inicio" />
				<input name="endTime" type="time" required aria-label="Fin" />
				<button type="submit" disabled={adding}>
					{adding ? "Agregando..." : "+ Franja"}
				</button>
			</form>
		</div>
	);
}
