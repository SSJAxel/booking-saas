import { Link } from "react-router-dom";

export default function NotFoundPage() {
	return (
		<div className="page status">
			<h1>No encontramos esta página</h1>
			<Link to="/" className="button-link">
				Volver al inicio
			</Link>
		</div>
	);
}
