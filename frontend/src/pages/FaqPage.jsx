import { Link } from "react-router-dom";
import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema, faqSchema } from "../structuredData.js";
import "./LegalPages.css";

const FAQS = [
	{
		q: "¿Qué tipo de negocios pueden usar CapiBooking?",
		a: "Cualquier negocio que atienda con turnos: peluquerías, barberías, salones de estética, spas, estudios de tatuajes, salones de uñas y consultorios, entre otros. Cada uno arma su propio catálogo de servicios, profesionales y horarios.",
	},
	{
		q: "¿Cómo reservo un turno?",
		a: "Entrá a la página de reserva del negocio (el link que te compartió, con el formato capibooking.com/reservar/nombre-del-negocio), elegí el servicio, el profesional y un horario disponible, y completá tus datos de contacto para confirmar.",
	},
	{
		q: "¿Necesito crear una cuenta para reservar un turno?",
		a: "No. Reservar un turno como cliente no requiere cuenta ni contraseña, solo tu nombre y un dato de contacto (email o teléfono).",
	},
	{
		q: "¿Cómo cancelo o reprogramo un turno?",
		a: "Contactá directamente al negocio con el que reservaste — ellos administran su agenda y pueden cancelar o mover tu turno. Sus datos de contacto están en su página de reserva.",
	},
	{
		q: "¿Qué es la seña y por qué algunos negocios la piden?",
		a: "Algunos negocios piden una seña (pago parcial) para confirmar el turno y reducir ausencias. Si el servicio que elegiste la requiere, la página te lo indica antes de confirmar la reserva.",
	},
	{
		q: "¿Voy a recibir un recordatorio de mi turno?",
		a: "Depende de cómo lo configuró cada negocio; muchos envían confirmaciones y recordatorios por email al canal de contacto que dejaste al reservar.",
	},
	{
		q: "Tengo un negocio, ¿cómo empiezo a usar CapiBooking?",
		a: "Mirá los planes disponibles y registrate desde ahí — podés empezar con el plan de prueba para conocer el panel antes de decidir.",
	},
	{
		q: "¿Puedo cambiar de plan más adelante?",
		a: "Sí, desde el panel de tu negocio podés ver tu plan actual y solicitar un cambio en cualquier momento.",
	},
];

export default function FaqPage() {
	return (
		<div className="legal-page">
			<Seo
				title="Preguntas frecuentes sobre turnos y reservas online — CapiBooking"
				description="Respuestas a las dudas más comunes sobre cómo reservar un turno, señas, recordatorios y cómo empezar a usar CapiBooking en tu peluquería, barbería o centro de estética."
				path="/preguntas-frecuentes"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Preguntas frecuentes" }]),
					faqSchema(FAQS),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Preguntas frecuentes y ayuda</h1>
				<p className="legal-updated">Respuestas rápidas a las dudas más comunes</p>

				{FAQS.map((item) => (
					<details className="faq-item" key={item.q}>
						<summary>{item.q}</summary>
						<p>{item.a}</p>
					</details>
				))}

				<div className="legal-contact">
					<p>
						<strong>¿No encontraste tu respuesta?</strong>
					</p>
					<p>
						Visitá <Link to="/soporte">Soporte</Link> o el <Link to="/manual-de-uso">Manual de uso</Link>.
					</p>
				</div>
			</main>

			<PublicFooter />
		</div>
	);
}
