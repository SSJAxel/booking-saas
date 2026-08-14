import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { api, resolveMediaUrl } from "../api.js";
import ReservationModal from "../components/ReservationModal.jsx";
import TeamCarousel from "../components/TeamCarousel.jsx";
import BranchSchedule from "../components/BranchSchedule.jsx";
import HeaderSearch from "../components/HeaderSearch.jsx";
import SideMenu from "../components/SideMenu.jsx";
import InstagramFeed from "../components/InstagramFeed.jsx";
import "./BookingPage.css";

const CalendarIcon = () => (
	<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<rect x="3" y="4" width="18" height="18" rx="3" />
		<path d="M16 2v4M8 2v4M3 10h18" />
	</svg>
);

const PinIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<path d="M12 21s7-6.6 7-12a7 7 0 1 0-14 0c0 5.4 7 12 7 12Z" />
		<circle cx="12" cy="9" r="2.5" />
	</svg>
);

const PhoneIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<path d="M4 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L14 13l5 2v4a2 2 0 0 1-2 2C9.6 21 3 14.4 3 6a2 2 0 0 1 1-2Z" />
	</svg>
);

/** Groups services by `service.category` — null/unset falls into a single "Servicios" bucket.
 * Services flagged `service.featured` also appear a second time, up front, in a synthetic
 * "Destacados" group (the featured flag doesn't exist on ServiceOffering yet — see the request to
 * Axel — so `s.featured` is always falsy today and this group simply never renders until it does). */
function groupByCategory(services) {
	const groups = new Map();
	const featured = services.filter((s) => s.featured);
	if (featured.length > 0) groups.set("Destacados", featured);
	for (const s of services) {
		const key = s.category || "Servicios";
		if (!groups.has(key)) groups.set(key, []);
		groups.get(key).push(s);
	}
	return Array.from(groups.entries()).map(([name, items]) => ({ name, items }));
}

function slugify(name) {
	return name
		.toLowerCase()
		.normalize("NFD")
		.replace(/[̀-ͯ]/g, "")
		.replace(/[^a-z0-9]+/g, "-");
}

export default function BookingPage() {
	const { tenantSlug } = useParams();

	const [tenant, setTenant] = useState(null);
	const [branches, setBranches] = useState([]);
	const [branch, setBranch] = useState(null);
	const [services, setServices] = useState([]);
	const [teamProfessionals, setTeamProfessionals] = useState([]);

	const [initializing, setInitializing] = useState(true);
	const [error, setError] = useState("");
	const [footerOpen, setFooterOpen] = useState({});
	const [bookingService, setBookingService] = useState(null);
	const [bookingProfessional, setBookingProfessional] = useState(null);
	const [menuOpen, setMenuOpen] = useState(false);

	useEffect(() => {
		let cancelled = false;
		async function load() {
			try {
				const [tenantData, branchList] = await Promise.all([
					api.public.tenant(tenantSlug),
					api.public.branches(tenantSlug),
				]);
				if (cancelled) return;
				setTenant(tenantData);
				setBranches(branchList);
				const firstBranch = branchList[0] ?? null;
				setBranch(firstBranch);
				const serviceList = await api.public.services(tenantSlug, firstBranch?.id);
				if (cancelled) return;
				setServices(serviceList);
				await loadTeam(serviceList, firstBranch, () => cancelled);
			} catch (err) {
				if (!cancelled) setError(err.message);
			} finally {
				if (!cancelled) setInitializing(false);
			}
		}
		load();
		return () => {
			cancelled = true;
		};
	}, [tenantSlug]);

	// Stand-in for a public "all professionals for a branch" endpoint, which doesn't exist yet —
	// /professionals requires a serviceId. Fetches every service's staff in parallel and merges by
	// id, so a professional only assigned to, say, the branch's 2nd service still shows up (a
	// single-service fetch was missing anyone not on that one service — e.g. Jacinto, assigned only
	// to "Lavado de barba", never appeared while the teaser only asked about "Corte de Barba").
	// Scoped to `forBranch` (not the `branch` state, which may not have updated yet by the time
	// this runs) so switching branches doesn't leave the previous branch's staff showing.
	async function loadTeam(serviceList, forBranch, isCancelled) {
		if (serviceList.length === 0) {
			setTeamProfessionals([]);
			return;
		}
		const results = await Promise.all(
			serviceList.map((s) => api.public.professionals(tenantSlug, s.id, forBranch?.id)),
		);
		if (isCancelled()) return;
		const byId = new Map();
		for (const staff of results) {
			for (const p of staff) byId.set(p.id, p);
		}
		setTeamProfessionals(
			Array.from(byId.values()).map((p) => ({ ...p, photoUrl: resolveMediaUrl(p.photoUrl) })),
		);
	}

	async function handlePickBranch(b) {
		setBranch(b);
		try {
			const serviceList = await api.public.services(tenantSlug, b.id);
			setServices(serviceList);
			await loadTeam(serviceList, b, () => false);
		} catch (err) {
			setError(err.message);
		}
	}

	const categories = useMemo(() => groupByCategory(services), [services]);

	function scrollToCategory(name) {
		const el = document.getElementById(`pb-cat-${slugify(name)}`);
		if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
	}

	function toggleFooterCol(key) {
		setFooterOpen((prev) => ({ ...prev, [key]: !prev[key] }));
	}

	if (initializing) {
		return (
			<div className="pb-root">
				<p className="pb-loading">Cargando...</p>
			</div>
		);
	}

	if (!tenant) {
		return (
			<div className="pb-root">
				<p className="pb-fatal-error">{error || "No pudimos cargar este negocio."}</p>
			</div>
		);
	}

	const accentStyle = tenant.accentColor ? { "--pb-accent": tenant.accentColor } : undefined;

	return (
		<div className="pb-root" style={accentStyle}>
			<header className="pb-header">
				<div className="pb-header-brand">
					{tenant.logoUrl ? (
						<img src={resolveMediaUrl(tenant.logoUrl)} alt={tenant.name} className="pb-header-logo" />
					) : (
						<span className="pb-header-logo-fallback">{tenant.name?.[0] ?? "?"}</span>
					)}
					<span className="pb-header-name">{tenant.name}</span>
				</div>
				<div className="pb-header-actions">
					<HeaderSearch categories={categories} services={services} professionals={teamProfessionals} />
					<button type="button" className="pb-header-icon-btn" aria-label="Menú" onClick={() => setMenuOpen(true)}>
						☰
					</button>
					<button type="button" className="pb-header-reservar" onClick={() => services[0] && setBookingService(services[0])}>
						<CalendarIcon />
						<span className="pb-header-reservar-label">RESERVAR</span>
					</button>
				</div>
			</header>

			<SideMenu
				open={menuOpen}
				onClose={() => setMenuOpen(false)}
				categories={categories}
				services={services}
				professionals={teamProfessionals}
				branches={branches}
				tenant={tenant}
				onReservar={() => services[0] && setBookingService(services[0])}
			/>

			<section
				className="pb-hero"
				style={tenant.bannerUrl ? { backgroundImage: `url(${resolveMediaUrl(tenant.bannerUrl)})` } : undefined}
			>
				<div className="pb-hero-card pb-glass">
					{tenant.logoUrl && <img src={resolveMediaUrl(tenant.logoUrl)} alt="" className="pb-hero-logo" />}
					<h1 className="pb-hero-name">{tenant.name}</h1>
					{tenant.tagline && <p className="pb-hero-tagline">{tenant.tagline}</p>}
					<div className="pb-hero-actions">
						<button type="button" className="pb-hero-btn-outline" onClick={() => scrollToCategory(categories[0]?.name ?? "Servicios")}>
							SERVICIOS
						</button>
						<button
							type="button"
							className="pb-hero-btn-solid"
							onClick={() => services[0] && setBookingService(services[0])}
						>
							RESERVAR
						</button>
					</div>
				</div>
			</section>

			<div className="pb-layout">
				<main className="pb-catalog">
					{categories.length > 1 && (
						<nav className="pb-tabs">
							{categories.map((c) => (
								<button key={c.name} type="button" className="pb-tab" onClick={() => scrollToCategory(c.name)}>
									{c.name}
								</button>
							))}
						</nav>
					)}

					{error && <p className="pb-error">{error}</p>}

					{services.length === 0 && <p className="pb-empty">No hay servicios disponibles.</p>}

					{categories.map((cat) => (
						<section key={cat.name} id={`pb-cat-${slugify(cat.name)}`} className="pb-category">
							<h2 className="pb-category-header">
								{cat.name.toUpperCase()}
								<span className="pb-category-count">({cat.items.length})</span>
							</h2>
							<div className="pb-service-list">
								{cat.items.map((s) => (
									<article key={s.id} id={`pb-service-${s.id}`} className="pb-service-card">
										<div className="pb-service-top">
											<h3 className="pb-service-name">{s.name}</h3>
											<button
												type="button"
												className="pb-reservar-btn"
												onClick={() => setBookingService(s)}
												aria-label={`Reservar ${s.name}`}
											>
												<CalendarIcon />
												<span className="pb-reservar-label">RESERVAR</span>
											</button>
										</div>
										<div className="pb-service-meta">
											<span className="pb-service-duration">{s.durationMinutes} min</span>
											<span className="pb-service-price">${Number(s.price).toLocaleString("es-AR")}</span>
										</div>
										{s.description && <p className="pb-service-desc">{s.description}</p>}
									</article>
								))}
							</div>
						</section>
					))}
				</main>

				<aside className="pb-sidebar">
					<div className="pb-sidebar-card">
						{branches.length > 1 && (
							<div className="pb-branch-tabs">
								{branches.map((b) => (
									<button
										key={b.id}
										type="button"
										className={`pb-branch-tab${branch?.id === b.id ? " active" : ""}`}
										onClick={() => handlePickBranch(b)}
									>
										{b.name}
									</button>
								))}
							</div>
						)}
						{branch && (
							<div className="pb-branch-info">
								{branch.address && (
									<div className="pb-branch-info-row">
										<PinIcon />
										<span>{branch.address}</span>
									</div>
								)}
								{branch.phone && (
									<div className="pb-branch-info-row">
										<PhoneIcon />
										<span>{branch.phone}</span>
									</div>
								)}
								<BranchSchedule hours={branch.hours} />
							</div>
						)}
						{branch?.address && (
							<>
								<iframe
									className="pb-map-frame"
									src={`https://www.google.com/maps?q=${encodeURIComponent(branch.address)}&output=embed`}
									title="Mapa"
									loading="lazy"
								/>
								<a
									className="pb-map-link"
									href={
										branch.googleBusinessUrl ||
										`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(branch.address)}`
									}
									target="_blank"
									rel="noopener noreferrer"
								>
									{branch.googleBusinessUrl ? "Ver perfil de Google" : "Cómo llegar"}
								</a>
							</>
						)}
					</div>
				</aside>
			</div>

			<TeamCarousel professionals={teamProfessionals} onSelect={setBookingProfessional} />

			<InstagramFeed scriptSrc={tenant.instagramFeedUrl} />

			<footer className="pb-footer">
				<div className="pb-footer-inner">
					<div className="pb-footer-col">
						<div className="pb-footer-brand">CapiBooking</div>
						<p className="pb-footer-tag">Tu marca, tu estilo, todo en un solo lugar.</p>
					</div>
					<div className={`pb-footer-col${footerOpen.info ? " open" : ""}`}>
						<h4 onClick={() => toggleFooterCol("info")}>Información</h4>
						<ul>
							<li>Política de privacidad</li>
							<li>Condiciones del servicio</li>
							<li>Condiciones de uso</li>
							<li>Mapa del sitio</li>
						</ul>
					</div>
					<div className={`pb-footer-col${footerOpen.ayudas ? " open" : ""}`}>
						<h4 onClick={() => toggleFooterCol("ayudas")}>Ayudas</h4>
						<ul>
							<li>Soporte</li>
							<li>Planes y precios</li>
							<li>Preguntas frecuentes y ayuda</li>
							<li>Manual de uso</li>
						</ul>
					</div>
					<div className={`pb-footer-col${footerOpen.mas ? " open" : ""}`}>
						<h4 onClick={() => toggleFooterCol("mas")}>Más servicios</h4>
						<ul>
							<li>CapiSpa</li>
							<li>CapiInk</li>
							<li>CapiNails</li>
							<li>Ver todos</li>
						</ul>
					</div>
				</div>
				<div className="pb-footer-bottom">© {new Date().getFullYear()} CapiBooking — todos los derechos reservados</div>
			</footer>

			{(bookingService || bookingProfessional) && (
				<ReservationModal
					tenant={tenant}
					tenantSlug={tenantSlug}
					branch={branch}
					service={bookingService}
					professional={bookingProfessional}
					onClose={() => {
						setBookingService(null);
						setBookingProfessional(null);
					}}
				/>
			)}
		</div>
	);
}
