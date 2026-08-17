import { SITE_URL, SITE_NAME } from "./seoConfig.js";

/** CapiBooking itself, as a piece of software — the schema Google/AI answer engines use to
 * understand "what is this site" regardless of which page someone lands on first. Included on
 * every public page (not just the home page) since a crawler doesn't always enter through "/". */
export function organizationSchema() {
	return {
		"@context": "https://schema.org",
		"@type": "SoftwareApplication",
		name: SITE_NAME,
		applicationCategory: "BusinessApplication",
		operatingSystem: "Web",
		url: SITE_URL,
		description:
			"Sistema de turnos online para peluquerías, barberías, salones de estética y spas: agenda, recordatorios automáticos y cobro de señas con Mercado Pago.",
		offers: {
			"@type": "Offer",
			price: "0",
			priceCurrency: "ARS",
			description: "Plan Demo gratuito, sin tarjeta",
		},
		areaServed: {
			"@type": "Country",
			name: "Argentina",
		},
	};
}

/** BreadcrumbList — helps both classic search-result breadcrumbs and GEO (gives an AI crawler the
 * page's place in the site without having to infer it from the URL). `items` is [{name, path}] in
 * order; `path` is optional on the last entry (Google's own examples leave the current page
 * unlinked). */
export function breadcrumbSchema(items) {
	return {
		"@context": "https://schema.org",
		"@type": "BreadcrumbList",
		itemListElement: items.map((item, i) => ({
			"@type": "ListItem",
			position: i + 1,
			name: item.name,
			...(item.path ? { item: `${SITE_URL}${item.path}` } : {}),
		})),
	};
}

/** FAQPage — lets FAQ answers surface directly in Google's rich results and gives AI answer
 * engines pre-formatted Q&A pairs to quote, which is most of what GEO optimization actually is.
 * `faqs` is [{q, a}]. */
export function faqSchema(faqs) {
	return {
		"@context": "https://schema.org",
		"@type": "FAQPage",
		mainEntity: faqs.map((faq) => ({
			"@type": "Question",
			name: faq.q,
			acceptedAnswer: {
				"@type": "Answer",
				text: faq.a,
			},
		})),
	};
}

/** HowTo — matches the step-by-step shape of /manual-de-uso 1:1, so a rich result (or an AI
 * assistant answering "cómo reservo un turno en CapiBooking") can quote the steps directly instead
 * of paraphrasing from plain paragraphs. `steps` is [{title, body}]. */
export function howToSchema({ name, description, steps }) {
	return {
		"@context": "https://schema.org",
		"@type": "HowTo",
		name,
		description,
		step: steps.map((step, i) => ({
			"@type": "HowToStep",
			position: i + 1,
			name: step.title,
			text: step.body,
		})),
	};
}

/** Organization — the founder/support contact identity, used alongside SoftwareApplication on
 * pages where "who runs this" matters most (Soporte, legal pages) for E-E-A-T trust signals. */
export function publisherSchema() {
	return {
		"@context": "https://schema.org",
		"@type": "Organization",
		name: "CapiByte",
		url: "https://capibyte.com",
		email: "info.capibyte@gmail.com",
		founder: SITE_NAME,
	};
}
