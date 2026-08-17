import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema } from "../structuredData.js";
import "./LegalPages.css";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

export default function TermsOfUsePage() {
	return (
		<div className="legal-page">
			<Seo
				title="Condiciones de uso — CapiBooking"
				description="Condiciones de uso del sitio CapiBooking y de las páginas de reserva de turnos de los negocios que operan en la plataforma."
				path="/condiciones-uso"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Condiciones de uso" }]),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Condiciones de uso</h1>
				<p className="legal-updated">Última actualización: 15 de agosto de 2026</p>

				<p>
					Estas condiciones aplican a cualquier persona que navegue el sitio de CapiBooking o las páginas de
					reserva de turnos de los negocios que usan la plataforma (por ejemplo, direcciones del tipo
					capibooking.com/reservar/tu-negocio). Al usar el sitio, aceptás estas condiciones.
				</p>

				<h2>1. Uso permitido</h2>
				<p>
					Podés usar CapiBooking para consultar disponibilidad y reservar turnos con los negocios que operan
					en la plataforma, dentro de los términos que cada negocio ofrece.
				</p>

				<h2>2. Uso indebido</h2>
				<ul>
					<li>No está permitido intentar vulnerar la seguridad de la plataforma ni acceder a datos ajenos.</li>
					<li>No está permitido usar la plataforma para enviar spam, contenido fraudulento o malicioso.</li>
					<li>
						No está permitido realizar reservas falsas o repetidas con la intención de perjudicar a un
						negocio.
					</li>
					<li>No está permitido copiar, revender o explotar comercialmente la plataforma sin autorización.</li>
				</ul>

				<h2>3. Reservas de turnos</h2>
				<p>
					Cada negocio define sus propios servicios, horarios, precios y políticas de cancelación. CapiBooking
					provee la herramienta técnica para gestionar la reserva, pero la relación comercial (prestación del
					servicio, pago, cancelaciones) es entre el usuario y el negocio elegido.
				</p>

				<h2>4. Contenido de terceros</h2>
				<p>
					Las páginas de reserva pueden incluir contenido embebido de terceros, como mapas de Google Maps o
					publicaciones de Instagram del negocio. Ese contenido está sujeto a los términos de esos servicios
					externos, ajenos a CapiBooking.
				</p>

				<h2>5. Propiedad intelectual</h2>
				<p>
					El nombre CapiBooking, su diseño y su código son propiedad de CapiByte. El contenido cargado por
					cada negocio (nombre, logo, imágenes, textos) es propiedad de ese negocio.
				</p>

				<h2>6. Enlaces y disponibilidad</h2>
				<p>
					No garantizamos que el sitio esté libre de interrupciones ni que el contenido publicado por los
					negocios sea siempre exacto o esté actualizado; cada negocio es responsable de mantener su
					información al día.
				</p>

				<h2>7. Ley aplicable</h2>
				<p>
					Estas condiciones se rigen por las leyes de la República Argentina. Cualquier controversia se
					someterá a los tribunales ordinarios competentes.
				</p>

				<div className="legal-contact">
					<p>
						<strong>¿Encontraste un problema en el sitio?</strong>
					</p>
					<p>
						Escribinos a <a href={`mailto:${FOUNDER_EMAIL}`}>{FOUNDER_EMAIL}</a>
					</p>
				</div>
			</main>

			<PublicFooter />
		</div>
	);
}
