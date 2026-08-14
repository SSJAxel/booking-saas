import { useEffect, useRef } from "react";

/** Embeds a third-party Instagram feed widget (Trustindex, Elfsight, SnapWidget, etc.) by dropping
 * its <script> tag inside our own container so it renders in place rather than wherever it'd land
 * if injected at the bottom of <body>. `scriptSrc` is expected to come from `tenant.instagramFeedUrl`
 * once that field exists (see the note to Axel) — nothing renders without it. */
export default function InstagramFeed({ scriptSrc }) {
	const containerRef = useRef(null);

	useEffect(() => {
		if (!scriptSrc || !containerRef.current) return;
		const script = document.createElement("script");
		script.src = scriptSrc;
		script.defer = true;
		script.async = true;
		containerRef.current.appendChild(script);
		return () => {
			containerRef.current?.replaceChildren();
		};
	}, [scriptSrc]);

	if (!scriptSrc) return null;

	return (
		<section className="pb-ig">
			<h2 className="pb-team-title">Instagram</h2>
			<div ref={containerRef} className="pb-ig-feed" />
		</section>
	);
}
