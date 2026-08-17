import { SITE_URL, SITE_NAME } from "../seoConfig.js";

/**
 * Per-page SEO tags for the public site — title, description, canonical, Open Graph/Twitter, and
 * optional JSON-LD structured data (for GEO: AI answer engines and rich results both read this).
 *
 * No react-helmet or similar: React 19 hoists <title>/<meta>/<link> into <head> natively, wherever
 * they're rendered in the tree, deduping by name/property — this can just render them inline. Only
 * <script type="application/ld+json"> doesn't get hoisted (React only hoists title/meta/link), but
 * Google/AI crawlers read JSON-LD from anywhere in the document, not just <head>, so that's fine
 * left in the body.
 */
export default function Seo({ title, description, path, type = "website", jsonLd }) {
	const url = `${SITE_URL}${path}`;
	const schemas = Array.isArray(jsonLd) ? jsonLd : jsonLd ? [jsonLd] : [];

	return (
		<>
			<title>{title}</title>
			<meta name="description" content={description} />
			<link rel="canonical" href={url} />

			<meta property="og:type" content={type} />
			<meta property="og:site_name" content={SITE_NAME} />
			<meta property="og:url" content={url} />
			<meta property="og:title" content={title} />
			<meta property="og:description" content={description} />
			<meta property="og:locale" content="es_AR" />

			<meta name="twitter:card" content="summary_large_image" />
			<meta name="twitter:title" content={title} />
			<meta name="twitter:description" content={description} />

			{schemas.map((schema, i) => (
				// eslint-disable-next-line react/no-danger
				<script
					key={i}
					type="application/ld+json"
					dangerouslySetInnerHTML={{ __html: JSON.stringify(schema) }}
				/>
			))}
		</>
	);
}
