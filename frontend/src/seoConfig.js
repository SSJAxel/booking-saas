// Single place to change once the product has its own domain — every canonical/OG/sitemap URL
// is built from this. Still on the Vercel subdomain (see conversation with the founder,
// 2026-08-15): swap SITE_URL here and regenerate public/sitemap.xml when the real domain is live.
export const SITE_URL = "https://booking-saas-inky.vercel.app";
export const SITE_NAME = "CapiBooking";

// Reused across <Seo> callers as a sensible baseline — every page still writes its own specific
// title/description, this is only what a page falls back to if it doesn't.
export const DEFAULT_TITLE = "CapiBooking — Sistema de turnos online para peluquerías, barberías y estética";
export const DEFAULT_DESCRIPTION =
	"Agenda online, recordatorios automáticos y cobro de señas con Mercado Pago para peluquerías, barberías, salones de estética, spas y estudios. Probá gratis, sin tarjeta.";
