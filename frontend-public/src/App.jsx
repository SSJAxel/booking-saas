import { Route, Routes } from "react-router-dom";
import LandingPage from "./pages/LandingPage.jsx";
import TenantHomePage from "./pages/TenantHomePage.jsx";
import BookingPage from "./pages/BookingPage.jsx";
import NotFoundPage from "./pages/NotFoundPage.jsx";

export default function App() {
	return (
		<Routes>
			<Route path="/" element={<LandingPage />} />
			<Route path="/:tenantSlug" element={<TenantHomePage />} />
			<Route path="/:tenantSlug/reservar/:serviceId" element={<BookingPage />} />
			<Route path="*" element={<NotFoundPage />} />
		</Routes>
	);
}
