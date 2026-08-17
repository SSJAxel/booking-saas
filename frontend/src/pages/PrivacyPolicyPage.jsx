import PublicHeader from "../components/PublicHeader.jsx";
import PublicFooter from "../components/PublicFooter.jsx";
import Seo from "../components/Seo.jsx";
import { organizationSchema, breadcrumbSchema } from "../structuredData.js";
import "./LegalPages.css";

const FOUNDER_EMAIL = "info.capibyte@gmail.com";

export default function PrivacyPolicyPage() {
	return (
		<div className="legal-page">
			<Seo
				title="Política de privacidad — CapiBooking"
				description="Cómo CapiBooking recopila, usa y protege los datos de negocios y clientes que reservan turnos, en línea con la Ley 25.326 de Protección de Datos Personales."
				path="/politica-privacidad"
				jsonLd={[
					organizationSchema(),
					breadcrumbSchema([{ name: "Inicio", path: "/" }, { name: "Política de privacidad" }]),
				]}
			/>

			<PublicHeader />

			<main className="legal-main">
				<h1>Política de privacidad</h1>
				<p className="legal-updated">Última actualización: 15 de agosto de 2026</p>

				<p>
					En CapiBooking (operado por CapiByte, en adelante "nosotros" o "la plataforma") nos tomamos en serio la
					protección de tus datos personales. Esta política explica qué información recopilamos, con qué
					finalidad la usamos y qué derechos tenés sobre ella, en línea con la Ley 25.326 de Protección de
					Datos Personales de la República Argentina.
				</p>

				<h2>1. Quiénes somos</h2>
				<p>
					CapiBooking es una plataforma de gestión de turnos y reservas online que utilizan negocios de
					servicios ("tenants") para administrar su agenda, y que a su vez es utilizada por los clientes
					finales de esos negocios para reservar turnos.
				</p>

				<h2>2. Qué datos recopilamos</h2>
				<ul>
					<li>
						<strong>De los negocios que usan CapiBooking:</strong> nombre del negocio, datos de contacto,
						dirección de sucursales, credenciales de acceso, información de facturación y plan contratado.
					</li>
					<li>
						<strong>De las personas que reservan un turno:</strong> nombre, teléfono y/o email, y los datos
						del turno (servicio, profesional, fecha y horario elegidos).
					</li>
					<li>
						<strong>Datos técnicos:</strong> dirección IP, tipo de dispositivo y navegador, y datos de uso
						recopilados automáticamente para el funcionamiento y la seguridad del servicio.
					</li>
				</ul>

				<h2>3. Con qué finalidad usamos tus datos</h2>
				<ul>
					<li>Permitir la creación, confirmación, modificación y recordatorio de turnos.</li>
					<li>Administrar la cuenta del negocio y su plan de suscripción.</li>
					<li>Enviar notificaciones operativas (confirmaciones, recordatorios, avisos de cambios).</li>
					<li>Mejorar la seguridad, el rendimiento y la calidad del servicio.</li>
					<li>Cumplir obligaciones legales y responder a requerimientos de autoridades competentes.</li>
				</ul>

				<h2>4. Con quién compartimos tus datos</h2>
				<p>
					Los datos de una reserva son compartidos con el negocio ante el cual se reserva el turno, ya que es
					quien necesita esa información para prestar el servicio. No vendemos datos personales a terceros.
					Podemos compartir información con proveedores tecnológicos que nos ayudan a operar la plataforma
					(por ejemplo, hosting o envío de notificaciones), bajo obligaciones de confidencialidad.
				</p>

				<h2>5. Cuánto tiempo conservamos tus datos</h2>
				<p>
					Conservamos los datos mientras la cuenta del negocio esté activa o mientras sea necesario para
					cumplir con las finalidades descriptas y con obligaciones legales o contables. Podés solicitar la
					eliminación de tus datos en cualquier momento, sujeto a las excepciones legales aplicables.
				</p>

				<h2>6. Tus derechos</h2>
				<p>
					De acuerdo con la Ley 25.326, tenés derecho a acceder, rectificar, actualizar y solicitar la
					supresión de tus datos personales. El titular de los datos personales tiene la facultad de ejercer
					el derecho de acceso de forma gratuita a intervalos no inferiores a seis meses, salvo que se
					acredite un interés legítimo al efecto. La Agencia de Acceso a la Información Pública, en su
					carácter de Órgano de Control de la Ley 25.326, tiene la atribución de atender las denuncias y
					reclamos que se interpongan con relación al incumplimiento de las normas sobre protección de
					datos personales.
				</p>

				<h2>7. Cookies y almacenamiento local</h2>
				<p>
					Usamos almacenamiento local del navegador para mantener tu sesión iniciada y recordar preferencias
					como el tema claro/oscuro. No utilizamos cookies de rastreo publicitario de terceros.
				</p>

				<h2>8. Cambios a esta política</h2>
				<p>
					Podemos actualizar esta política ocasionalmente. Si los cambios son significativos, lo
					comunicaremos a través de la plataforma o por email.
				</p>

				<div className="legal-contact">
					<p>
						<strong>¿Consultas sobre tus datos?</strong>
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
