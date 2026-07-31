import { Route, Routes } from "react-router-dom";
import LandingPage from "./pages/LandingPage.jsx";
import SignupPage from "./pages/SignupPage.jsx";
import TenantLayout from "./layout/TenantLayout.jsx";
import TenantHomePage from "./pages/TenantHomePage.jsx";
import BookingPage from "./pages/BookingPage.jsx";
import NotFoundPage from "./pages/NotFoundPage.jsx";

export default function App() {
	return (
		<Routes>
			<Route path="/" element={<LandingPage />} />
			<Route path="/registrarse" element={<SignupPage />} />
			<Route path="/:tenantSlug" element={<TenantLayout />}>
				<Route index element={<TenantHomePage />} />
				<Route path="reservar/:serviceId" element={<BookingPage />} />
			</Route>
			<Route path="*" element={<NotFoundPage />} />
		</Routes>
	);
}
