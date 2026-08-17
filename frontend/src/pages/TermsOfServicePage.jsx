import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema } from "../structuredData.js";
import "./LegalPages.css";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

export default function TermsOfServicePage() {
	return (
		<div className="legal-page">
			<Seo
				title="Condiciones del servicio — CapiBooking"
				description="Condiciones para negocios que contratan un plan de CapiBooking: facturación, responsabilidades de la cuenta, disponibilidad del servicio y baja."
				path="/condiciones-servicio"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Condiciones del servicio" }]),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Condiciones del servicio</h1>
				<p className="legal-updated">Última actualización: 15 de agosto de 2026</p>

				<p>
					Estas condiciones regulan la contratación y el uso de CapiBooking por parte de negocios que
					suscriben un plan para gestionar sus turnos y reservas online ("el cliente" o "el negocio"). Al
					crear una cuenta o contratar un plan, aceptás estas condiciones.
				</p>

				<h2>1. El servicio</h2>
				<p>
					CapiBooking ofrece un panel de administración de turnos, agenda, profesionales, sucursales y
					servicios, junto con una página de reservas online para que los clientes finales del negocio
					puedan reservar turnos.
				</p>

				<h2>2. Planes y facturación</h2>
				<ul>
					<li>Los planes disponibles y sus precios se publican en la página de precios de CapiBooking.</li>
					<li>
						El acceso a las funcionalidades de cada plan depende de los límites definidos para ese plan
						(por ejemplo, cantidad de profesionales o sucursales).
					</li>
					<li>
						La activación y renovación de un plan pago se coordina según el método de pago informado en la
						página de precios; el negocio es responsable de mantener su información de pago al día.
					</li>
					<li>
						Podemos ofrecer un período de prueba (plan TRIAL) sujeto a las condiciones vigentes al momento
						del registro.
					</li>
				</ul>

				<h2>3. Cuenta y responsabilidad del negocio</h2>
				<ul>
					<li>El negocio es responsable de la veracidad de los datos que carga en la plataforma.</li>
					<li>
						El negocio es responsable de la custodia de sus credenciales de acceso y de las acciones
						realizadas por los usuarios (dueño, administradores y staff) dentro de su cuenta.
					</li>
					<li>
						El negocio es responsable del contenido que publica (nombres de servicios, descripciones,
						imágenes, información de contacto) y de cumplir con la normativa aplicable a su actividad.
					</li>
				</ul>

				<h2>4. Disponibilidad del servicio</h2>
				<p>
					Trabajamos para mantener la plataforma disponible, pero no garantizamos un funcionamiento
					ininterrumpido o libre de errores. Podemos realizar tareas de mantenimiento que interrumpan
					temporalmente el servicio, procurando minimizar el impacto.
				</p>

				<h2>5. Suspensión y baja de cuenta</h2>
				<p>
					Podemos suspender o dar de baja una cuenta ante incumplimiento de estas condiciones, uso indebido
					de la plataforma o falta de pago sostenida. El negocio puede solicitar la baja de su cuenta en
					cualquier momento contactándonos.
				</p>

				<h2>6. Limitación de responsabilidad</h2>
				<p>
					CapiBooking es una herramienta de gestión de turnos. No somos parte de la relación comercial entre
					el negocio y sus clientes finales, y no respondemos por controversias derivadas de esa relación
					(por ejemplo, ausencias, cancelaciones o disconformidades sobre el servicio prestado por el
					negocio).
				</p>

				<h2>7. Modificaciones</h2>
				<p>
					Podemos actualizar estas condiciones para reflejar cambios en el servicio o en la normativa
					aplicable. Los cambios relevantes se comunicarán a través de la plataforma o por email.
				</p>

				<div className="legal-contact">
					<p>
						<strong>¿Consultas sobre tu plan o tu cuenta?</strong>
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
