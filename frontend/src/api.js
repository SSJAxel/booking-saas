const API_BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

// Uploaded images (logo/banner/professional photo) are stored as paths relative to this API
// (e.g. "/uploads/public/..."), not full URLs — an <img src> needs the API's own origin prefixed
// on. Left untouched if it's already an absolute URL (someone pasted an external link instead).
export function resolveMediaUrl(path) {
	if (!path) return null;
	return /^https?:\/\//.test(path) ? path : `${API_BASE}${path}`;
}

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
		throw new Error((data?.message || `La solicitud falló (${res.status})`) + detail);
	}
	return data;
}

async function requestForm(path, formData) {
	const headers = {};
	const token = getToken();
	if (token) headers.Authorization = `Bearer ${token}`;
	// No Content-Type here on purpose — the browser sets multipart/form-data with the boundary
	// itself; setting it manually would drop the boundary and break parsing server-side.
	const res = await fetch(`${API_BASE}${path}`, { method: "POST", headers, body: formData });
	if (res.status === 204) return null;
	const data = await res.json().catch(() => null);
	if (!res.ok) {
		throw new Error(data?.message || `La solicitud falló (${res.status})`);
	}
	return data;
}

async function requestBlob(path) {
	const token = getToken();
	const headers = token ? { Authorization: `Bearer ${token}` } : {};
	const res = await fetch(`${API_BASE}${path}`, { headers });
	if (!res.ok) throw new Error(`La solicitud falló (${res.status})`);
	return res.blob();
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
	me: {
		get: () => request("/api/me"),
		update: (body) => request("/api/me", { method: "PATCH", body }),
	},
	tenant: {
		get: () => request("/api/tenant"),
		plans: () => request("/api/plans", { auth: false }),
		changePlan: (planTier) => request("/api/tenant/plan", { method: "PATCH", body: { planTier } }),
		subscribe: (planTier) => request("/api/tenant/subscription", { method: "POST", body: { planTier } }),
		connectMercadoPago: () => request("/api/tenant/mercadopago/connect"),
		mercadoPagoStatus: () => request("/api/tenant/mercadopago/status"),
		disconnectMercadoPago: () => request("/api/tenant/mercadopago/connect", { method: "DELETE" }),
		updateBranding: (body) => request("/api/tenant/branding", { method: "PATCH", body }),
		uploadLogo: (file) => {
			const fd = new FormData();
			fd.append("file", file);
			return requestForm("/api/tenant/branding/logo", fd);
		},
		uploadBanner: (file) => {
			const fd = new FormData();
			fd.append("file", file);
			return requestForm("/api/tenant/branding/banner", fd);
		},
		updateTimezone: (timezone) => request("/api/tenant/timezone", { method: "PATCH", body: { timezone } }),
		updateNotifications: (whatsappEnabled) =>
			request("/api/tenant/notifications", { method: "PATCH", body: { whatsappEnabled } }),
		updateClientRanking: (topClientsThreshold, topClientsCount) =>
			request("/api/tenant/client-ranking", { method: "PATCH", body: { topClientsThreshold, topClientsCount } }),
		updateHistoryRetention: (historyRetentionMonths) =>
			request("/api/tenant/history-retention", { method: "PATCH", body: { historyRetentionMonths } }),
	},
	branches: {
		list: () => request("/api/branches"),
		create: (body) => request("/api/branches", { method: "POST", body }),
		update: (id, body) => request(`/api/branches/${id}`, { method: "PUT", body }),
		delete: (id) => request(`/api/branches/${id}`, { method: "DELETE" }),
		listHours: (id) => request(`/api/branches/${id}/hours`),
		addHours: (id, body) => request(`/api/branches/${id}/hours`, { method: "POST", body }),
		deleteHours: (id, hoursId) => request(`/api/branches/${id}/hours/${hoursId}`, { method: "DELETE" }),
	},
	professionals: {
		list: () => request("/api/professionals"),
		create: (body) => request("/api/professionals", { method: "POST", body }),
		update: (id, body) => request(`/api/professionals/${id}`, { method: "PUT", body }),
		delete: (id) => request(`/api/professionals/${id}`, { method: "DELETE" }),
		listAvailability: (id) => request(`/api/professionals/${id}/availability`),
		addAvailability: (id, body) => request(`/api/professionals/${id}/availability`, { method: "POST", body }),
		deleteAvailability: (id, availabilityId) =>
			request(`/api/professionals/${id}/availability/${availabilityId}`, { method: "DELETE" }),
		listTimeOff: (id) => request(`/api/professionals/${id}/time-off`),
		addTimeOff: (id, body) => request(`/api/professionals/${id}/time-off`, { method: "POST", body }),
		deleteTimeOff: (id, timeOffId) => request(`/api/professionals/${id}/time-off/${timeOffId}`, { method: "DELETE" }),
		listDateAvailability: (id) => request(`/api/professionals/${id}/date-availability`),
		addDateAvailability: (id, body) => request(`/api/professionals/${id}/date-availability`, { method: "POST", body }),
		deleteDateAvailability: (id, dateAvailabilityId) =>
			request(`/api/professionals/${id}/date-availability/${dateAvailabilityId}`, { method: "DELETE" }),
		uploadPhoto: (id, file) => {
			const fd = new FormData();
			fd.append("file", file);
			return requestForm(`/api/professionals/${id}/photo`, fd);
		},
	},
	reports: {
		today: () => request("/api/reports/today"),
		traffic: (days = 7) => request(`/api/reports/traffic${toQuery({ days })}`),
		productSales: (days = 7) => request(`/api/reports/product-sales${toQuery({ days })}`),
		clients: () => request("/api/reports/clients"),
	},
	services: {
		list: () => request("/api/services"),
		create: (body) => request("/api/services", { method: "POST", body }),
		update: (id, body) => request(`/api/services/${id}`, { method: "PUT", body }),
		delete: (id) => request(`/api/services/${id}`, { method: "DELETE" }),
		listProfessionals: (id) => request(`/api/services/${id}/professionals`),
		assignProfessional: (id, professionalId) =>
			request(`/api/services/${id}/professionals`, { method: "POST", body: { professionalId } }),
		unassignProfessional: (id, professionalId) =>
			request(`/api/services/${id}/professionals/${professionalId}`, { method: "DELETE" }),
	},
	products: {
		list: () => request("/api/products"),
		create: (body) => request("/api/products", { method: "POST", body }),
		update: (id, body) => request(`/api/products/${id}`, { method: "PUT", body }),
		delete: (id) => request(`/api/products/${id}`, { method: "DELETE" }),
	},
	sales: {
		create: (body) => request("/api/sales", { method: "POST", body }),
	},
	appointments: {
		list: (params) => request(`/api/appointments${toQuery(params)}`),
		transition: (id, status) => request(`/api/appointments/${id}/status`, { method: "PATCH", body: { status } }),
		confirmDeposit: (id) => request(`/api/appointments/${id}/confirm-deposit`, { method: "PATCH" }),
		create: (body) => request("/api/appointments", { method: "POST", body }),
		reschedule: (id, body) => request(`/api/appointments/${id}/reschedule`, { method: "POST", body }),
		createOvertime: (body) => request("/api/appointments/overtime", { method: "POST", body }),
		purgeHistory: (window) => request(`/api/appointments/history${toQuery({ window })}`, { method: "DELETE" }),
		delete: (id) => request(`/api/appointments/${id}`, { method: "DELETE" }),
	},
	clients: {
		search: (q) => request(`/api/clients/search${toQuery({ q })}`),
		setPinned: (id, pinned) => request(`/api/clients/${id}/pin`, { method: "PATCH", body: { pinned } }),
	},
	support: {
		report: (message, imageFile) => {
			const fd = new FormData();
			fd.append("message", message);
			fd.append("image", imageFile);
			return requestForm("/api/support-reports", fd);
		},
		requestPlanUpgrade: (note) => request("/api/support-reports/plan-upgrade", { method: "POST", body: { note } }),
	},
	admin: {
		tenants: () => request("/api/admin/tenants"),
		updateTenantPlan: (id, planTier) => request(`/api/admin/tenants/${id}/plan`, { method: "PATCH", body: { planTier } }),
		approveTenant: (id) => request(`/api/admin/tenants/${id}/approve`, { method: "POST" }),
		updateTenantNextPaymentDue: (id, nextPaymentDueAt) =>
			request(`/api/admin/tenants/${id}/next-payment-due`, { method: "PATCH", body: { nextPaymentDueAt } }),
		supportReports: () => request("/api/admin/support-reports"),
		resolveReport: (id, resolved) =>
			request(`/api/admin/support-reports/${id}/resolved`, { method: "PATCH", body: { resolved } }),
		deleteReport: (id) => request(`/api/admin/support-reports/${id}`, { method: "DELETE" }),
		reportImageBlob: (id) => requestBlob(`/api/admin/support-reports/${id}/image`),
		updateReportPriority: (id, priority) =>
			request(`/api/admin/support-reports/${id}/priority`, { method: "PATCH", body: { priority } }),
		updateTenantCustomPrice: (id, customMonthlyPrice) =>
			request(`/api/admin/tenants/${id}/custom-price`, { method: "PATCH", body: { customMonthlyPrice } }),
		updateTenantProfessionalLimit: (id, professionalLimitOverride) =>
			request(`/api/admin/tenants/${id}/professional-limit`, { method: "PATCH", body: { professionalLimitOverride } }),
		tenantDetail: (id) => request(`/api/admin/tenants/${id}/detail`),
		tenantsUsage: () => request("/api/admin/tenants/usage"),
		mrr: () => request("/api/admin/mrr"),
		pricingReference: () => request("/api/admin/pricing-reference"),
		// Not requestBlob: needs the caller-provided filename for the download, and blob-only
		// endpoints don't currently expose Content-Disposition parsing — kept as an explicit fetch.
		billingReportBlob: (from, to) =>
			requestBlob(`/api/admin/billing-report${toQuery({ from, to })}`),
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
