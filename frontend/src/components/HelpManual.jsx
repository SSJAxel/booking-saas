import { useEffect } from "react";

/**
 * Content lives here as data (not scattered across each page) so the manual stays accurate as a
 * single source of truth — a new button/section gets one entry here instead of inline tooltips
 * spread across six files that are easy to forget to update.
 */
const SECTIONS = [
	{
		title: "Turnos",
		intro:
			"Todos los turnos reservados por clientes desde tu página pública, o cargados a mano. Es la pantalla del día a día.",
		items: [
			{
				what: "Filtro «Estado»",
				how: "Elegís un estado del desplegable y la lista se filtra sola.",
				why: "Con varios turnos mezclados, lo que importa el día a día es «qué tengo pendiente» o «qué se viene hoy», no la lista completa.",
			},
			{
				what: "Colores de estado (badges)",
				how: "Pendiente (amarillo) espera confirmación o pago de seña · Confirmado (verde) va en firme · Cancelado / No asistió (rojo) no va a pasar · Completado (gris) ya se hizo.",
				why: "Un vistazo a los colores alcanza para saber qué necesita atención sin leer cada fila.",
			},
			{
				what: "Columna «Pago»",
				how: "Muestra si ese turno pedía seña y si ya se pagó (Pendiente / Pagado / No requiere / Reembolsado).",
				why: "Separado del estado del turno porque un turno puede estar confirmado por otro motivo aunque la seña siga sin llegar (o viceversa).",
			},
			{
				what: "Botones de acción (Confirmar, Cancelar, Completar, No asistió)",
				how: "Solo aparecen las transiciones válidas desde el estado actual — por ejemplo, desde Pendiente solo podés Confirmar o Cancelar, nunca marcar Completado directamente.",
				why: "Evita estados imposibles, como dar por completado un turno que nunca se confirmó — la regla de negocio protege esto aunque quisieras forzarlo.",
			},
		],
	},
	{
		title: "Sucursales",
		intro: "Cada sucursal es un local físico donde atendés. Si tenés uno solo, con una alcanza y sobra.",
		items: [
			{
				what: "Formulario «Agregar» (Nombre, Dirección, Teléfono)",
				how: "Nombre es obligatorio; dirección y teléfono son opcionales y se muestran en tu página pública si los cargás.",
				why: "Cada profesional se asocia a una sucursal — necesitás al menos una antes de poder cargar profesionales.",
			},
			{
				what: "Columna «Activa»",
				how: "Solo informativa por ahora.",
				why: "Todavía no hay un botón para desactivar una sucursal desde el panel — si necesitás dar una de baja, es un cambio manual en la base.",
			},
		],
	},
	{
		title: "Profesionales",
		intro:
			"Quien atiende los turnos — un peluquero, un tatuador, quien sea. Los servicios se asignan a profesionales puntuales, y cada turno es siempre con uno de ellos.",
		items: [
			{
				what: "Formulario «Agregar» (Sucursal, Nombre, Bio)",
				how: "La sucursal es obligatoria y se elige de las que ya creaste. La bio es opcional y se muestra en la reserva pública.",
				why: "Sin al menos una sucursal cargada, no vas a poder crear ningún profesional.",
			},
			{
				what: "«Agregar horario semanal» (dentro de la tarjeta de cada profesional)",
				how: "Elegís día, hora de inicio y hora de fin, y sumás. Podés cargar varios bloques el mismo día (por ejemplo 9-13 y 15-19 si hay corte al mediodía).",
				why: "Esto es lo que determina qué horarios ve un cliente como disponibles al reservar — sin cargar disponibilidad, nadie puede reservar con ese profesional aunque exista en el sistema.",
			},
		],
	},
	{
		title: "Servicios",
		intro:
			"Lo que tu negocio ofrece — un corte, una sesión, lo que sea. Cada servicio define cuánto dura, cuánto cuesta, y si pide seña o no.",
		items: [
			{
				what: "Formulario «Agregar» (Nombre, Descripción, Duración, Precio, Seña)",
				how: "Duración en minutos define cuánto ocupa el turno en la agenda del profesional. La seña es opcional: si la cargás, el cliente tiene que pagarla por Mercado Pago para que el turno pase a confirmado; si la dejás vacía, el turno se confirma solo, sin pago de por medio.",
				why: "La seña existe para reducir las ausencias de clientes que reservan y después no aparecen — un problema típico en peluquerías, salones y estudios.",
			},
			{
				what: "Chips «Profesionales que lo ofrecen»",
				how: "Un click en un profesional lo prende o apaga (toggle) como habilitado para ese servicio.",
				why: "Un cliente solo puede reservar ese servicio con un profesional marcado acá — si no marcás a nadie, nadie va a poder reservarlo en la página pública.",
			},
		],
	},
	{
		title: "Productos",
		intro: "Stock de productos que vendés además de los turnos (por ejemplo, productos de venta en el local).",
		items: [
			{
				what: "Indicador «Plan X · N/límite productos»",
				how: "Muestra cuántos productos activos tenés contra el límite de tu plan.",
				why: "El plan BASIC (gratis) tiene tope de 5 productos activos; PRO no tiene límite — es una de las cosas que diferencia los planes.",
			},
			{
				what: "Formulario «Agregar» (Nombre, Precio, Stock)",
				how: "Carga un producto nuevo con su stock inicial.",
				why: "En BASIC, si ya llegaste al límite, este formulario va a rechazar la carga hasta que subas de plan o desactives otro producto.",
			},
			{
				what: "Botón «Vender»",
				how: "Elegís cantidad y confirmás — resta del stock y queda registrada la venta. Se desactiva solo si no queda stock.",
				why: "Es la forma de que las ventas de mostrador (no ligadas a un turno) también queden reflejadas en el sistema.",
			},
		],
	},
	{
		title: "Mi Plan",
		intro:
			"Configuración general del negocio: suscripción, cobros con Mercado Pago, marca de tu página pública, zona horaria y notificaciones.",
		items: [
			{
				what: "«Pasar a BASIC» / «Suscribirme a PRO»",
				how: "Solo el dueño de la cuenta los ve. Suscribirme a PRO te manda a Mercado Pago a autorizar un cobro recurrente mensual; Pasar a BASIC cancela la suscripción activa y te vuelve al plan gratis.",
				why: "El pasaje a un plan pago siempre pasa por una autorización real de cobro — no es un simple interruptor, para que nunca quede un tenant «PRO» sin estar pagando.",
			},
			{
				what: "Zona horaria",
				how: "Solo dueño o admin pueden cambiarla, escribiendo un identificador tipo «America/Argentina/Buenos_Aires» (hay sugerencias comunes en el desplegable).",
				why: "Todos los horarios del negocio (disponibilidad, turnos) se interpretan en esta zona — si está mal configurada, los horarios que ve un cliente al reservar pueden no coincidir con la hora real.",
			},
			{
				what: "«Conectar Mercado Pago»",
				how: "Solo el dueño. Te manda a autorizar tu propia cuenta de Mercado Pago.",
				why: "Sin conectar, las señas y la suscripción se cobran a una cuenta compartida de la plataforma. Conectando la tuya, ese dinero llega directo a tu cuenta.",
			},
			{
				what: "Marca en tu sitio público (logo, color, frase)",
				how: "Dueño o admin. Cualquier campo que dejes vacío simplemente no se muestra.",
				why: "Es lo que ve tu cliente en tu página pública de reservas — hace que se sienta tu marca y no un formulario genérico.",
			},
			{
				what: "Notificaciones — WhatsApp",
				how: "Dueño o admin activa o desactiva el aviso por WhatsApp. Requiere que el cliente haya dejado su teléfono al reservar.",
				why: "Es un canal extra, nunca un reemplazo del mail: aunque lo tengas apagado, el cliente siempre recibe el mail de confirmación/cancelación igual.",
			},
		],
	},
	{
		title: "Menú lateral",
		intro: "Lo que siempre está visible, en cualquier pantalla.",
		items: [
			{
				what: "Claro / Oscuro",
				how: "Cambia la apariencia del panel. Queda guardado en este navegador.",
				why: "Preferencia personal — no afecta a nadie más que a vos.",
			},
			{
				what: "Salir",
				how: "Cierra tu sesión y vuelve al login.",
				why: "Usalo en una computadora compartida para no dejar la cuenta abierta.",
			},
		],
	},
];

export default function HelpManual({ open, onClose }) {
	useEffect(() => {
		if (!open) return;
		function onKeyDown(event) {
			if (event.key === "Escape") onClose();
		}
		document.addEventListener("keydown", onKeyDown);
		return () => document.removeEventListener("keydown", onKeyDown);
	}, [open, onClose]);

	if (!open) return null;

	return (
		<div className="modal-backdrop" onClick={onClose}>
			<div className="modal-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
				<div className="modal-header">
					<h2>Manual del panel</h2>
					<button type="button" className="modal-close" onClick={onClose} aria-label="Cerrar manual">
						×
					</button>
				</div>
				<div className="modal-body">
					{SECTIONS.map((section) => (
						<details key={section.title} className="manual-section">
							<summary>{section.title}</summary>
							<p className="muted">{section.intro}</p>
							{section.items.map((item) => (
								<div className="manual-item" key={item.what}>
									<p className="manual-item-title">{item.what}</p>
									<p>
										<strong>Cómo se usa: </strong>
										{item.how}
									</p>
									<p>
										<strong>Por qué: </strong>
										{item.why}
									</p>
								</div>
							))}
						</details>
					))}
				</div>
			</div>
		</div>
	);
}
