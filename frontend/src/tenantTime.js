// Every appointment timestamp from the API is an absolute Instant (UTC ISO string). Rendering it
// with plain Date getters (getHours(), toLocaleString() without timeZone, toISOString()) converts
// it using the VIEWING BROWSER's own zone, not the tenant's configured business zone — harmless
// when they happen to match, but a silent multi-hour shift the moment they don't (a tenant whose
// timezone is still the "UTC" default, or a browser/device set to a different zone than the
// business itself). Every wall-clock read of an appointment time must go through here instead.

function partsInZone(isoString, timezone) {
	const date = new Date(isoString);
	const parts = new Intl.DateTimeFormat("en-US", {
		timeZone: timezone || "UTC",
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		hourCycle: "h23",
	}).formatToParts(date);
	const get = (type) => Number(parts.find((p) => p.type === type).value);
	return { year: get("year"), month: get("month"), day: get("day"), hour: get("hour"), minute: get("minute") };
}

/** "yyyy-MM-dd" in the tenant's zone — for bucketing appointments into calendar days/date inputs. */
export function tenantDateKey(isoString, timezone) {
	const { year, month, day } = partsInZone(isoString, timezone);
	return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** Minutes since midnight in the tenant's zone — for positioning a block in the week grid. */
export function tenantMinutesOfDay(isoString, timezone) {
	const { hour, minute } = partsInZone(isoString, timezone);
	return hour * 60 + minute;
}

/** "HH:MM" in the tenant's zone. */
export function tenantTimeLabel(isoString, timezone) {
	const { hour, minute } = partsInZone(isoString, timezone);
	return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

/** "d/M/yyyy HH:MM" in the tenant's zone — compact list-view format. */
export function tenantDateTimeLabel(isoString, timezone) {
	const { day, month, year, hour, minute } = partsInZone(isoString, timezone);
	return `${day}/${month}/${year} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

/** "lunes, 17 de agosto, 16:30" in the tenant's zone — long-form display (detail modal). */
export function tenantLongDateTimeLabel(isoString, timezone) {
	const date = new Date(isoString);
	const formatted = new Intl.DateTimeFormat("es-AR", {
		timeZone: timezone || "UTC",
		weekday: "long",
		day: "numeric",
		month: "long",
		hour: "2-digit",
		minute: "2-digit",
	}).format(date);
	return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}
