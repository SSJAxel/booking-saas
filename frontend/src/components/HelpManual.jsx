import { useEffect } from "react";
import { ADMIN_MANUAL_SECTIONS as SECTIONS } from "../adminManualContent.js";

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
