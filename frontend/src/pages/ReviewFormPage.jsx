import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api.js";

const STARS = [1, 2, 3, 4, 5];

export default function ReviewFormPage() {
	const [searchParams] = useSearchParams();
	const tenantSlug = searchParams.get("tenant");
	const token = searchParams.get("token");
	const linkIncomplete = !tenantSlug || !token;

	const [invite, setInvite] = useState(null);
	const [loadError, setLoadError] = useState("");
	const [rating, setRating] = useState(0);
	const [hoverRating, setHoverRating] = useState(0);
	const [comment, setComment] = useState("");
	const [submitting, setSubmitting] = useState(false);
	const [submitError, setSubmitError] = useState("");
	const [submitted, setSubmitted] = useState(false);

	useEffect(() => {
		if (linkIncomplete) return;
		api.public
			.reviewInvite(tenantSlug, token)
			.then(setInvite)
			.catch((err) => setLoadError(err.message));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function handleSubmit(event) {
		event.preventDefault();
		if (rating === 0) {
			setSubmitError("Elegí una cantidad de estrellas.");
			return;
		}
		setSubmitError("");
		setSubmitting(true);
		try {
			await api.public.submitReview(tenantSlug, token, { rating, comment: comment || null });
			setSubmitted(true);
		} catch (err) {
			setSubmitError(err.message);
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<div className="auth-page">
			<div className="auth-card">
				<h1>Dejá tu reseña</h1>
				{linkIncomplete ? (
					<p className="error">Este link está incompleto.</p>
				) : submitted ? (
					<p className="muted">¡Gracias por tu reseña! Nos ayuda mucho a seguir mejorando.</p>
				) : loadError ? (
					<p className="error">{loadError}</p>
				) : !invite ? (
					<p className="muted">Cargando...</p>
				) : (
					<>
						<p className="muted">
							Hola {invite.clientName}, contanos cómo estuvo tu turno de "{invite.serviceName}" con{" "}
							{invite.professionalName}.
						</p>
						<form onSubmit={handleSubmit}>
							<div className="star-rating" role="radiogroup" aria-label="Calificación">
								{STARS.map((star) => (
									<button
										key={star}
										type="button"
										className="star-button"
										aria-label={`${star} estrellas`}
										aria-pressed={rating >= star}
										onMouseEnter={() => setHoverRating(star)}
										onMouseLeave={() => setHoverRating(0)}
										onClick={() => setRating(star)}
									>
										{(hoverRating || rating) >= star ? "★" : "☆"}
									</button>
								))}
							</div>
							<label>
								Comentario (opcional)
								<textarea
									value={comment}
									onChange={(e) => setComment(e.target.value)}
									maxLength={1000}
									rows={4}
								/>
							</label>
							{submitError && <p className="error">{submitError}</p>}
							<button type="submit" disabled={submitting}>
								{submitting ? "Enviando..." : "Enviar reseña"}
							</button>
						</form>
					</>
				)}
			</div>
		</div>
	);
}
