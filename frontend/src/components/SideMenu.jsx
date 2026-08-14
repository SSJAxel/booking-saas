import { useState } from "react";

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

function whatsappHref(phone) {
	return `https://wa.me/${phone.replace(/\D/g, "")}`;
}

const SearchIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<circle cx="11" cy="11" r="7" />
		<path d="m21 21-4.3-4.3" />
	</svg>
);

const WhatsAppIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
		<path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.39 1.26 4.81L2 22l5.42-1.36a9.9 9.9 0 0 0 4.62 1.15h.01c5.46 0 9.91-4.45 9.91-9.91C21.96 6.45 17.5 2 12.04 2Zm5.8 14c-.24.68-1.4 1.3-1.94 1.35-.5.05-1.14.07-1.84-.12-.42-.11-.97-.31-1.67-.6-2.93-1.27-4.84-4.24-4.98-4.44-.15-.2-1.19-1.58-1.19-3.02s.76-2.15 1.03-2.44c.27-.29.6-.36.8-.36l.57.01c.18 0 .43-.07.67.51.24.58.83 2.02.9 2.16.07.15.12.32.02.52-.1.2-.15.32-.3.5-.15.17-.31.39-.44.52-.15.15-.3.31-.13.61.17.29.76 1.25 1.63 2.02 1.12 1 2.06 1.31 2.36 1.46.3.15.48.13.65-.08.18-.2.75-.87.95-1.17.2-.29.4-.24.67-.15.27.1 1.71.81 2 .96.3.15.49.22.56.35.07.13.07.75-.17 1.43Z" />
	</svg>
);

const PinIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<path d="M12 21s7-6.6 7-12a7 7 0 1 0-14 0c0 5.4 7 12 7 12Z" />
		<circle cx="12" cy="9" r="2.5" />
	</svg>
);

const InstagramIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<rect x="3" y="3" width="18" height="18" rx="5" />
		<circle cx="12" cy="12" r="4" />
		<circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none" />
	</svg>
);

const FacebookIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
		<path d="M15 3h-2a4 4 0 0 0-4 4v3H7v4h2v7h4v-7h3l1-4h-4V7a1 1 0 0 1 1-1h3z" />
	</svg>
);

/** Left-anchored slide-in menu — white glass (vs. the dark .pb-glass used on the hero/team hover)
 * since it sits over the light catalog background, not a photo. Has its own inline search (same
 * three lists as the header's HeaderSearch — duplicated rather than shared since one renders as a
 * floating dropdown and the other inline in an already-open panel), category links, "Equipo", a
 * Reservar CTA, every branch's contact info (WhatsApp + address, one block per branch, divided),
 * and social links. Instagram/Facebook URLs aren't in the public API yet (no such Tenant field
 * exists) — those icons only render once `tenant.instagramUrl`/`tenant.facebookUrl` show up, see
 * the note left for Axel. */
export default function SideMenu({ open, onClose, categories, services, professionals, branches, tenant, onReservar }) {
	const [query, setQuery] = useState("");

	if (!open) return null;

	const results = (() => {
		const q = query.trim().toLowerCase();
		if (!q) return null;
		return {
			categories: categories.filter((c) => c.name.toLowerCase().includes(q)),
			services: services.filter((s) => s.name.toLowerCase().includes(q)),
			professionals: professionals.filter((p) => p.displayName.toLowerCase().includes(q)),
		};
	})();

	function goTo(id) {
		scrollToId(id);
		onClose();
	}

	return (
		<div className="pb-sidemenu-backdrop" onClick={onClose}>
			<nav className="pb-sidemenu pb-glass-light" onClick={(e) => e.stopPropagation()}>
				<button type="button" className="pb-sidemenu-close" onClick={onClose} aria-label="Cerrar menú">
					×
				</button>

				<div className="pb-sidemenu-search">
					<SearchIcon />
					<input
						type="text"
						placeholder="Buscar..."
						value={query}
						onChange={(e) => setQuery(e.target.value)}
					/>
				</div>

				{results ? (
					<div className="pb-sidemenu-results">
						{results.categories.length === 0 && results.services.length === 0 && results.professionals.length === 0 && (
							<p className="pb-search-empty">Sin resultados.</p>
						)}
						{results.categories.map((c) => (
							<button key={`c-${c.name}`} type="button" className="pb-sidemenu-result" onClick={() => goTo(`pb-cat-${slugify(c.name)}`)}>
								{c.name}
							</button>
						))}
						{results.services.map((s) => (
							<button key={`s-${s.id}`} type="button" className="pb-sidemenu-result" onClick={() => goTo(`pb-service-${s.id}`)}>
								{s.name}
							</button>
						))}
						{results.professionals.map((p) => (
							<button key={`p-${p.id}`} type="button" className="pb-sidemenu-result" onClick={() => goTo("pb-team")}>
								{p.displayName}
							</button>
						))}
					</div>
				) : (
					<ul className="pb-sidemenu-list">
						{categories.map((c) => (
							<li key={c.name}>
								<button type="button" onClick={() => goTo(`pb-cat-${slugify(c.name)}`)}>
									{c.name}
								</button>
							</li>
						))}
						<li>
							<button type="button" onClick={() => goTo("pb-team")}>
								Equipo
							</button>
						</li>
					</ul>
				)}

				<button
					type="button"
					className="pb-sidemenu-reservar"
					onClick={() => {
						onReservar();
						onClose();
					}}
				>
					RESERVAR
				</button>

				{branches?.length > 0 && (
					<div className="pb-sidemenu-branches">
						{branches.map((b) => (
							<div key={b.id} className="pb-sidemenu-branch">
								<p className="pb-sidemenu-branch-name">{b.name}</p>
								{b.phone && (
									<a className="pb-sidemenu-contact-row" href={whatsappHref(b.phone)} target="_blank" rel="noopener noreferrer">
										<WhatsAppIcon />
										<span>{b.phone}</span>
									</a>
								)}
								{b.address && (
									<div className="pb-sidemenu-contact-row">
										<PinIcon />
										<span>{b.address}</span>
									</div>
								)}
							</div>
						))}
					</div>
				)}

				{(tenant.instagramUrl || tenant.facebookUrl) && (
					<div className="pb-sidemenu-social">
						{tenant.instagramUrl && (
							<a href={tenant.instagramUrl} target="_blank" rel="noopener noreferrer" aria-label="Instagram">
								<InstagramIcon />
							</a>
						)}
						{tenant.facebookUrl && (
							<a href={tenant.facebookUrl} target="_blank" rel="noopener noreferrer" aria-label="Facebook">
								<FacebookIcon />
							</a>
						)}
					</div>
				)}
			</nav>
		</div>
	);
}
