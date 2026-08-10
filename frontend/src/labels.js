// Español visible al usuario para los valores crudos que devuelve la API (enums en inglés a
// propósito en el backend/DB — ver TenantService.create y AppointmentStatus). Centralizado acá
// porque se usa en más de una pantalla y no queremos que cada una traduzca por su cuenta.

const STATUS_LABELS = {
	PENDING: "Pendiente",
	CONFIRMED: "Confirmado",
	CANCELLED: "Cancelado",
	COMPLETED: "Completado",
	NO_SHOW: "No asistió",
};

export function statusLabel(status) {
	return STATUS_LABELS[status] ?? status;
}

const PAYMENT_STATUS_LABELS = {
	PENDING: "Pendiente",
	PAID: "Pagado",
	NOT_REQUIRED: "No requiere",
	REFUNDED: "Reembolsado",
};

export function paymentStatusLabel(status) {
	return PAYMENT_STATUS_LABELS[status] ?? status;
}

// TRIAL mantiene su nombre interno (columna de DB, enum, contrato de API) — solo lo que ve el
// tenant/founder en pantalla dice "Demo". Ver el Javadoc de TenantService.create.
export function planLabel(tier) {
	return tier === "TRIAL" ? "Demo" : tier;
}

const SUBSCRIPTION_STATUS_LABELS = {
	PENDING: "Pendiente",
	AUTHORIZED: "Autorizada",
	PAUSED: "Pausada",
	CANCELLED: "Cancelada",
};

export function subscriptionStatusLabel(status) {
	if (!status) return "—";
	return SUBSCRIPTION_STATUS_LABELS[status] ?? status;
}
