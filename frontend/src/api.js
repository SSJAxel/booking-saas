const API_BASE = "http://localhost:8080";

function getToken() {
	return localStorage.getItem("token");
}

async function request(path, { method = "GET", body, auth = true } = {}) {
	const headers = { "Content-Type": "application/json" };
	if (auth) {
		const token = getToken();
		if (token) headers.Authorization = `Bearer ${token}`;
	}
	const res = await fetch(`${API_BASE}${path}`, {
		method,
		headers,
		body: body !== undefined ? JSON.stringify(body) : undefined,
	});
	if (res.status === 204) return null;
	const data = await res.json().catch(() => null);
	if (!res.ok) {
		const detail = data?.fieldErrors?.length
			? ": " + data.fieldErrors.map((f) => `${f.field} ${f.message}`).join(", ")
			: "";
		throw new Error((data?.message || `Request failed (${res.status})`) + detail);
	}
	return data;
}

function toQuery(params = {}) {
	const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "");
	if (entries.length === 0) return "";
	return "?" + new URLSearchParams(entries).toString();
}

export const api = {
	register: (body) => request("/api/auth/register", { method: "POST", body, auth: false }),
	login: (body) => request("/api/auth/login", { method: "POST", body, auth: false }),
	verifyEmail: (body) => request("/api/auth/verify-email", { method: "POST", body, auth: false }),
	resendVerification: (body) => request("/api/auth/resend-verification", { method: "POST", body, auth: false }),
	me: () => request("/api/me"),
	tenant: {
		get: () => request("/api/tenant"),
		changePlan: (planTier) => request("/api/tenant/plan", { method: "PATCH", body: { planTier } }),
		subscribe: (planTier) => request("/api/tenant/subscription", { method: "POST", body: { planTier } }),
		connectMercadoPago: () => request("/api/tenant/mercadopago/connect"),
		updateBranding: (body) => request("/api/tenant/branding", { method: "PATCH", body }),
		updateTimezone: (timezone) => request("/api/tenant/timezone", { method: "PATCH", body: { timezone } }),
		updateNotifications: (whatsappEnabled) =>
			request("/api/tenant/notifications", { method: "PATCH", body: { whatsappEnabled } }),
	},
	branches: {
		list: () => request("/api/branches"),
		create: (body) => request("/api/branches", { method: "POST", body }),
	},
	professionals: {
		list: () => request("/api/professionals"),
		create: (body) => request("/api/professionals", { method: "POST", body }),
		listAvailability: (id) => request(`/api/professionals/${id}/availability`),
		addAvailability: (id, body) => request(`/api/professionals/${id}/availability`, { method: "POST", body }),
	},
	services: {
		list: () => request("/api/services"),
		create: (body) => request("/api/services", { method: "POST", body }),
		listProfessionals: (id) => request(`/api/services/${id}/professionals`),
		assignProfessional: (id, professionalId) =>
			request(`/api/services/${id}/professionals`, { method: "POST", body: { professionalId } }),
		unassignProfessional: (id, professionalId) =>
			request(`/api/services/${id}/professionals/${professionalId}`, { method: "DELETE" }),
	},
	products: {
		list: () => request("/api/products"),
		create: (body) => request("/api/products", { method: "POST", body }),
	},
	sales: {
		create: (body) => request("/api/sales", { method: "POST", body }),
	},
	appointments: {
		list: (params) => request(`/api/appointments${toQuery(params)}`),
		transition: (id, status) => request(`/api/appointments/${id}/status`, { method: "PATCH", body: { status } }),
	},
	public: {
		tenant: (tenantSlug) => request(`/api/public/${tenantSlug}`, { auth: false }),
		branches: (tenantSlug) => request(`/api/public/${tenantSlug}/branches`, { auth: false }),
		services: (tenantSlug, branchId) =>
			request(`/api/public/${tenantSlug}/services${toQuery({ branchId })}`, { auth: false }),
		professionals: (tenantSlug, serviceId, branchId) =>
			request(`/api/public/${tenantSlug}/professionals${toQuery({ serviceId, branchId })}`, { auth: false }),
		availability: (tenantSlug, professionalId, serviceId, date) =>
			request(`/api/public/${tenantSlug}/availability${toQuery({ professionalId, serviceId, date })}`, {
				auth: false,
			}),
		book: (tenantSlug, body) => request(`/api/public/${tenantSlug}/appointments`, { method: "POST", body, auth: false }),
	},
};
