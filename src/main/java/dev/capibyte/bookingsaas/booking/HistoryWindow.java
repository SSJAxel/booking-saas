package dev.capibyte.bookingsaas.booking;

/** Las cuatro ventanas de "borrar historial" que el dueño puede elegir desde Turnos → Lista — ver
 * AppointmentService#purgeHistory para qué instante de corte resuelve cada una. Deliberadamente sin
 * lógica acá: la traducción a un Instant de corte necesita "ahora" como input, que no pertenece a
 * una constante de enum — mismo split que RescheduleReason/ClientRatingService. */
public enum HistoryWindow {
	LAST_HOUR, LAST_24_HOURS, LAST_4_WEEKS, ALL
}
