import { useEffect, useState } from "react";
import { api } from "../api.js";

/** Fetches and shows the tenant's visible reviews on the public booking page — same "nothing
 * without it" convention as InstagramFeed: renders null while loading, on error, and when the
 * tenant has no reviews yet (including while the feature is simply disabled, since the API
 * already returns an empty list in that case — see ReviewService#publicReviews). */
export default function PublicReviews({ tenantSlug }) {
	const [reviews, setReviews] = useState([]);

	useEffect(() => {
		let cancelled = false;
		api.public
			.publicReviews(tenantSlug)
			.then((data) => {
				if (!cancelled) setReviews(data);
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, [tenantSlug]);

	if (reviews.length === 0) return null;

	const average = reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length;

	return (
		<section className="pb-reviews">
			<h2 className="pb-team-title">Reseñas</h2>
			<p className="pb-reviews-average">
				{average.toFixed(1)} ★ · {reviews.length} reseña{reviews.length === 1 ? "" : "s"}
			</p>
			<div className="pb-reviews-track">
				{reviews.map((r, i) => (
					<div key={i} className="pb-reviews-card pb-glass-light">
						<div className="pb-reviews-stars">{"★".repeat(r.rating)}{"☆".repeat(5 - r.rating)}</div>
						{r.comment && <p className="pb-reviews-comment">"{r.comment}"</p>}
						<p className="pb-reviews-client">
							{r.clientName} · {r.professionalName}
						</p>
					</div>
				))}
			</div>
		</section>
	);
}
