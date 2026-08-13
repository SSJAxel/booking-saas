import { useEffect, useState } from "react";
import { api } from "../api.js";

/** Same content-as-data pattern as the owner-facing HelpManual.jsx, kept as a separate component
 * (not a shared one with different `sections` props) since the admin and owner manuals cover
 * completely different surfaces and audiences — no shared content to actually reuse. */
const STATIC_SECTIONS = [
	{
		title: "MRR (ingreso mensual recurrente)",
		intro:
			"MRR = Monthly Recurring Revenue: cuánto factura la plataforma por mes si nada cambia. Se " +
			"muestra arriba de la tabla de «Cuentas», total y desglosado por plan.",
		items: [
			{
				what: "Cómo se calcula",
				how: "Se suma el precio efectivo (precio custom si tiene, si no el precio de lista vigente del plan) de cada tenant con estado Activo. Un tenant Suspendido o Pendiente de aprobación no suma, aunque tenga un plan pago cargado — no se le está cobrando de verdad.",
				why: "Es la forma estándar en SaaS de ver «cuánto entra por mes» de un vistazo, sin sumar factura por factura — y separar MRR total de MRR por plan ayuda a ver qué tan cargado está el negocio hacia PRO/MAX vs. los planes de entrada.",
			},
			{
				what: "Por qué puede no coincidir con lo que realmente cobra Mercado Pago",
				how: "El MRR usa el precio efectivo de cada tenant (de acá), no lo que su Preapproval de Mercado Pago tiene autorizado en este momento.",
				why: "Un cambio de precio (por indexación al dólar blue, o un precio negociado nuevo) no reautoriza automáticamente el cobro recurrente ya existente — el MRR es «lo que debería estar cobrando», el cobro real se pone al día recién cuando el tenant se re-suscribe.",
			},
		],
	},
	{
		title: "Amigos de la casa",
		intro:
			"Algunos negocios (hoy: Lusi Tattoo y Fadep Barber Studio) tienen un trato especial: usan el " +
			"plan MAX (todos los límites y funciones abiertos) pero se les cobra el precio de BASIC.",
		items: [
			{
				what: "Cómo se configura",
				how: "Desde «Cuentas»: plan = MAX, y en la columna «Precio» se carga a mano el valor de BASIC vigente (no queda atado automáticamente — si BASIC se reindexa por el dólar blue, hay que revisar y actualizar este número también).",
				why: "Es la misma mecánica de precio negociado que ya existe para cualquier tenant — no hay un botón especial de «amigo de la casa», es una combinación de plan + precio custom.",
			},
			{
				what: "Por qué nunca tienen fecha de vencimiento",
				how: "El campo «Vence» se deja vacío a propósito para estos negocios.",
				why: "No están en un ciclo de cobro normal (no pagan por Mercado Pago un monto que vence tal día) — es un trato manual, así que no tiene sentido que el sistema calcule «días restantes» sobre una fecha que no existe.",
			},
		],
	},
];

export default function AdminHelpManual({ open, onClose }) {
	const [reference, setReference] = useState(null);
	const [error, setError] = useState("");

	useEffect(() => {
		if (!open) return;
		api.admin
			.pricingReference()
			.then(setReference)
			.catch((err) => setError(err.message));
	}, [open]);

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
					<details className="manual-section" open>
						<summary>Precios y dólar blue</summary>
						<p className="muted">
							Los precios de PERSONAL/BASIC/PRO/MAX están atados al dólar blue: se fijan a una referencia
							(arrancó en $1.480) y solo se recalculan cuando el blue real se mueve $115 o más para arriba
							o para abajo de esa referencia. Cuando eso pasa, el nuevo valor del blue pasa a ser la
							referencia para el próximo salto — no es un ajuste diario, es por umbral.
						</p>
						<div className="manual-item">
							<p className="manual-item-title">Referencia vigente ahora mismo</p>
							{error ? (
								<p className="error">{error}</p>
							) : reference ? (
								<p>
									<strong>${Number(reference.referenceBlueRate).toLocaleString("es-AR")}</strong> — última
									actualización: {new Date(reference.updatedAt).toLocaleString("es-AR")}
								</p>
							) : (
								<p className="muted">Cargando...</p>
							)}
						</div>
					</details>
					{STATIC_SECTIONS.map((section) => (
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
