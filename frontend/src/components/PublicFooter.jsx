import { useState } from "react";
import { Link } from "react-router-dom";
import "./PublicFooter.css";

/**
 * Same footer as the tenant booking page (BookingPage.jsx), reused on every other public page
 * (pricing, legal, support, FAQ, manual, sitemap) so the site reads as one place instead of a
 * pricing page bolted onto a pile of one-off legal pages. Self-contained mobile accordion state
 * (each column collapses on narrow screens) since it's now rendered from several unrelated pages.
 */
export default function PublicFooter() {
	const [openCols, setOpenCols] = useState({});

	function toggleCol(key) {
		setOpenCols((prev) => ({ ...prev, [key]: !prev[key] }));
	}

	return (
		<footer className="pub-footer">
			<div className="pub-footer-inner">
				<div className="pub-footer-col">
					<img src="/favicon.svg" alt="" style={{ width: "60px" }} />
					<div className="pub-footer-brand">CapiBooking</div>
					<p className="pub-footer-tag">Reservá tu momento. Nosotros organizamos el resto</p>
				</div>
				<div className={`pub-footer-col${openCols.info ? " open" : ""}`}>
					<h4 onClick={() => toggleCol("info")}>Información</h4>
					<ul>
						<li>
							<Link to="/politica-privacidad">Política de privacidad</Link>
						</li>
						<li>
							<Link to="/condiciones-servicio">Condiciones del servicio</Link>
						</li>
						<li>
							<Link to="/condiciones-uso">Condiciones de uso</Link>
						</li>
						<li>
							<Link to="/mapa-sitio">Mapa del sitio</Link>
						</li>
					</ul>
				</div>
				<div className={`pub-footer-col${openCols.ayudas ? " open" : ""}`}>
					<h4 onClick={() => toggleCol("ayudas")}>Ayudas</h4>
					<ul>
						<li>
							<Link to="/soporte">Soporte</Link>
						</li>
						<li>
							<Link to="/">Planes y precios</Link>
						</li>
						<li>
							<Link to="/preguntas-frecuentes">Preguntas frecuentes y ayuda</Link>
						</li>
						<li>
							<Link to="/manual-de-uso">Manual de uso</Link>
						</li>
					</ul>
				</div>
				<div className={`pub-footer-col${openCols.mas ? " open" : ""}`}>
					<h4 onClick={() => toggleCol("mas")}>Más servicios</h4>
					<ul>
						<li>CapiSpa</li>
						<li>CapiInk</li>
						<li>CapiNails</li>
						<li>Ver todos</li>
					</ul>
				</div>
			</div>
			<div className="pub-footer-bottom">© {new Date().getFullYear()} CapiBooking — todos los derechos reservados</div>
		</footer>
	);
}
