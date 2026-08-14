import { useMemo, useState } from "react";

const SearchIcon = () => (
	<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<circle cx="11" cy="11" r="7" />
		<path d="m21 21-4.3-4.3" />
	</svg>
);

function slugify(name) {
	return name
		.toLowerCase()
		.normalize("NFD")
		.replace(/[̀-ͯ]/g, "")
		.replace(/[^a-z0-9]+/g, "-");
}

function scrollToId(id) {
	const el = document.getElementById(id);
	if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
}

/** Header search — one query box against three unrelated lists (category names, service names,
 * professional names) rather than a real search index, since the whole catalog is already loaded
 * client-side. Picking a result just scrolls to it (category/service sections and the team
 * carousel all already have stable ids from BookingPage.jsx/TeamCarousel.jsx). */
export default function HeaderSearch({ categories, services, professionals }) {
	const [open, setOpen] = useState(false);
	const [query, setQuery] = useState("");

	const results = useMemo(() => {
		const q = query.trim().toLowerCase();
		if (!q) return null;
		return {
			categories: categories.filter((c) => c.name.toLowerCase().includes(q)),
			services: services.filter((s) => s.name.toLowerCase().includes(q)),
			professionals: professionals.filter((p) => p.displayName.toLowerCase().includes(q)),
		};
	}, [query, categories, services, professionals]);

	function close() {
		setOpen(false);
		setQuery("");
	}

	function pickCategory(name) {
		scrollToId(`pb-cat-${slugify(name)}`);
		close();
	}

	return (
		<div className="pb-search">
			<button
				type="button"
				className="pb-header-icon-btn"
				aria-label="Buscar"
				onClick={() => setOpen((v) => !v)}
			>
				<SearchIcon />
			</button>
			{open && (
				<>
					<div className="pb-search-backdrop" onClick={close} />
					<div className="pb-search-panel pb-glass">
						<input
							type="text"
							className="pb-search-input"
							placeholder="Buscar categoría, servicio o profesional..."
							value={query}
							onChange={(e) => setQuery(e.target.value)}
							autoFocus
						/>
						{results && (
							<div className="pb-search-results">
								{results.categories.length === 0 && results.services.length === 0 && results.professionals.length === 0 && (
									<p className="pb-search-empty">Sin resultados.</p>
								)}
								{results.categories.length > 0 && (
									<div className="pb-search-group">
										<p className="pb-search-group-title">Categorías</p>
										{results.categories.map((c) => (
											<button key={c.name} type="button" onClick={() => pickCategory(c.name)}>
												{c.name}
											</button>
										))}
									</div>
								)}
								{results.services.length > 0 && (
									<div className="pb-search-group">
										<p className="pb-search-group-title">Servicios</p>
										{results.services.map((s) => (
											<button
												key={s.id}
												type="button"
												onClick={() => {
													scrollToId(`pb-service-${s.id}`);
													close();
												}}
											>
												{s.name}
											</button>
										))}
									</div>
								)}
								{results.professionals.length > 0 && (
									<div className="pb-search-group">
										<p className="pb-search-group-title">Equipo</p>
										{results.professionals.map((p) => (
											<button
												key={p.id}
												type="button"
												onClick={() => {
													scrollToId("pb-team");
													close();
												}}
											>
												{p.displayName}
											</button>
										))}
									</div>
								)}
							</div>
						)}
					</div>
				</>
			)}
		</div>
	);
}
