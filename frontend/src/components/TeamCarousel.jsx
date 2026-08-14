/** Employee carousel — name always visible on the bottom bar; hover reveals the professional's
 * bio as an overlay. `photoUrl` arrives already resolved to an absolute URL (BookingPage.jsx runs
 * it through resolveMediaUrl before passing professionals down) — falls back to a plain color tile
 * when a professional hasn't uploaded one. There's still no public "weekly hours summary" endpoint,
 * so the hover overlay shows the bio rather than real schedule text. Clicking a card opens the
 * booking modal pre-filled with that professional, asking which of their services next. */
export default function TeamCarousel({ professionals, onSelect }) {
	if (!professionals || professionals.length === 0) return null;

	return (
		<section className="pb-team" id="pb-team">
			<h2 className="pb-team-title">Equipo</h2>
			<div className="pb-team-track">
				{professionals.map((p) => (
					<button
						key={p.id}
						type="button"
						className="pb-team-card"
						style={p.photoUrl ? { backgroundImage: `url(${p.photoUrl})` } : undefined}
						onClick={() => onSelect(p)}
						aria-label={`Reservar con ${p.displayName}`}
					>
						<div className="pb-team-hours pb-glass">{p.bio || "Sin horarios cargados"}</div>
						<div className="pb-team-name-bar">{p.displayName}</div>
					</button>
				))}
			</div>
		</section>
	);
}
