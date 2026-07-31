const API_BASE = "http://localhost:8080";

async function request(path, { method = "GET", body } = {}) {
	const res = await fetch(`${API_BASE}${path}`, {
		method,
		headers: { "Content-Type": "application/json" },
		body: body !== undefined ? JSON.stringify(body) : undefined,
	});
	const data = await res.json().catch(() => null);
	if (!res.ok) {
		throw new Error(data?.message || `Request failed (${res.status})`);
	}
	return data;
}

export const api = {
	getPlans: () => request("/api/plans"),
	register: (body) => request("/api/auth/register", { method: "POST", body }),
	getTenant: (slug) => request(`/api/public/${slug}`),
	getBranches: (slug) => request(`/api/public/${slug}/branches`),
	getServices: (slug, branchId) =>
		request(`/api/public/${slug}/services${branchId ? `?branchId=${branchId}` : ""}`),
	getProfessionals: (slug, serviceId, branchId) =>
		request(
			`/api/public/${slug}/professionals?serviceId=${serviceId}` + (branchId ? `&branchId=${branchId}` : ""),
		),
	getAvailability: (slug, professionalId, serviceId, date) =>
		request(
			`/api/public/${slug}/availability?professionalId=${professionalId}&serviceId=${serviceId}&date=${date}`,
		),
	book: (slug, body) => request(`/api/public/${slug}/appointments`, { method: "POST", body }),
};
