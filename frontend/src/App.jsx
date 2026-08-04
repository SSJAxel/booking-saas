import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext.jsx";
import DashboardLayout from "./layout/DashboardLayout.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import VerifyEmailPage from "./pages/VerifyEmailPage.jsx";
import AppointmentsPage from "./pages/AppointmentsPage.jsx";
import BranchesPage from "./pages/BranchesPage.jsx";
import ProfessionalsPage from "./pages/ProfessionalsPage.jsx";
import ServicesPage from "./pages/ServicesPage.jsx";
import ProductsPage from "./pages/ProductsPage.jsx";
import TenantPage from "./pages/TenantPage.jsx";
import AccountPage from "./pages/AccountPage.jsx";
import BookingPage from "./pages/BookingPage.jsx";

function RequireAuth({ children }) {
	const { session } = useAuth();
	if (!session) return <Navigate to="/login" replace />;
	return children;
}

export default function App() {
	return (
		<Routes>
			<Route path="/login" element={<LoginPage />} />
			<Route path="/verificar-email" element={<VerifyEmailPage />} />
			<Route path="/reservar/:tenantSlug" element={<BookingPage />} />
			<Route
				path="/"
				element={
					<RequireAuth>
						<DashboardLayout />
					</RequireAuth>
				}
			>
				<Route index element={<Navigate to="appointments" replace />} />
				<Route path="appointments" element={<AppointmentsPage />} />
				<Route path="branches" element={<BranchesPage />} />
				<Route path="professionals" element={<ProfessionalsPage />} />
				<Route path="services" element={<ServicesPage />} />
				<Route path="products" element={<ProductsPage />} />
				<Route path="tenant" element={<TenantPage />} />
				<Route path="account" element={<AccountPage />} />
			</Route>
		</Routes>
	);
}
