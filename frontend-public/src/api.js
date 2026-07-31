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
	getTenant: (slug) => request(`/api/public/${slug}`),
	getServices: (slug) => request(`/api/public/${slug}/services`),
	getProfessionals: (slug, serviceId) =>
		request(`/api/public/${slug}/professionals?serviceId=${serviceId}`),
	getAvailability: (slug, professionalId, serviceId, date) =>
		request(
			`/api/public/${slug}/availability?professionalId=${professionalId}&serviceId=${serviceId}&date=${date}`,
		),
	book: (slug, body) => request(`/api/public/${slug}/appointments`, { method: "POST", body }),
};
