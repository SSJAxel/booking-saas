import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext.jsx";
import DashboardLayout from "./layout/DashboardLayout.jsx";
import AdminLayout from "./layout/AdminLayout.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import PricingPage from "./pages/PricingPage.jsx";
import VerifyEmailPage from "./pages/VerifyEmailPage.jsx";
import ForgotPasswordPage from "./pages/ForgotPasswordPage.jsx";
import ResetPasswordPage from "./pages/ResetPasswordPage.jsx";
import ReviewFormPage from "./pages/ReviewFormPage.jsx";
import AppointmentsPage from "./pages/AppointmentsPage.jsx";
import BranchesPage from "./pages/BranchesPage.jsx";
import ProfessionalsPage from "./pages/ProfessionalsPage.jsx";
import ServicesPage from "./pages/ServicesPage.jsx";
import ProductsPage from "./pages/ProductsPage.jsx";
import TenantPage from "./pages/TenantPage.jsx";
import AccountPage from "./pages/AccountPage.jsx";
import ReviewsPage from "./pages/ReviewsPage.jsx";
import BookingPage from "./pages/BookingPage.jsx";
import AdminTenantsPage from "./pages/AdminTenantsPage.jsx";
import AdminSupportReportsPage from "./pages/AdminSupportReportsPage.jsx";
import AdminUsagePage from "./pages/AdminUsagePage.jsx";
import DashboardHomePage from "./pages/DashboardHomePage.jsx";
import PrivacyPolicyPage from "./pages/PrivacyPolicyPage.jsx";
import TermsOfServicePage from "./pages/TermsOfServicePage.jsx";
import TermsOfUsePage from "./pages/TermsOfUsePage.jsx";
import SitemapPage from "./pages/SitemapPage.jsx";
import SupportPage from "./pages/SupportPage.jsx";
import FaqPage from "./pages/FaqPage.jsx";
import UserManualPage from "./pages/UserManualPage.jsx";
import AdminManualPage from "./pages/AdminManualPage.jsx";

function RequireAuth({ children }) {
	const { session } = useAuth();
	if (!session) return <Navigate to="/login" replace />;
	// A platform admin has no tenant-scoped data of their own to see here — send them to /admin.
	if (session.platformAdmin) return <Navigate to="/admin" replace />;
	return children;
}

// "Inicio" (stats/charts) has the same business-data sensitivity as /api/reports, which is
// OWNER/ADMIN-only — STAFF keeps landing on Turnos like before, same as they can't see the link.
function IndexRedirect() {
	const { session } = useAuth();
	const canSeeDashboard = session.role === "OWNER" || session.role === "ADMIN";
	return <Navigate to={canSeeDashboard ? "dashboard" : "appointments"} replace />;
}

function RequirePlatformAdmin({ children }) {
	const { session } = useAuth();
	if (!session) return <Navigate to="/login" replace />;
	// Not "/" anymore — that's the public marketing home now, not a tenant-scoped dashboard a
	// logged-in non-admin would land in usefully. Send them back to their own panel instead.
	if (!session.platformAdmin) return <Navigate to="/panel" replace />;
	return children;
}

export default function App() {
	return (
		<Routes>
			<Route path="/" element={<PricingPage />} />
			{/* Old URL, kept as a redirect (not a second route rendering the same page) so there's
			    only one canonical URL for search engines to index. */}
			<Route path="/precios" element={<Navigate to="/" replace />} />
			<Route path="/login" element={<LoginPage />} />
			<Route path="/verificar-email" element={<VerifyEmailPage />} />
			<Route path="/olvide-password" element={<ForgotPasswordPage />} />
			<Route path="/restablecer-password" element={<ResetPasswordPage />} />
			<Route path="/dejar-resena" element={<ReviewFormPage />} />
			<Route path="/reservar/:tenantSlug" element={<BookingPage />} />
			<Route path="/politica-privacidad" element={<PrivacyPolicyPage />} />
			<Route path="/condiciones-servicio" element={<TermsOfServicePage />} />
			<Route path="/condiciones-uso" element={<TermsOfUsePage />} />
			<Route path="/mapa-sitio" element={<SitemapPage />} />
			<Route path="/soporte" element={<SupportPage />} />
			<Route path="/preguntas-frecuentes" element={<FaqPage />} />
			<Route path="/manual-de-uso" element={<UserManualPage />} />
			<Route path="/manual-del-panel" element={<AdminManualPage />} />
			<Route
				path="/panel"
				element={
					<RequireAuth>
						<DashboardLayout />
					</RequireAuth>
				}
			>
				<Route index element={<IndexRedirect />} />
				<Route path="dashboard" element={<DashboardHomePage />} />
				<Route path="appointments" element={<AppointmentsPage />} />
				<Route path="branches" element={<BranchesPage />} />
				<Route path="professionals" element={<ProfessionalsPage />} />
				<Route path="services" element={<ServicesPage />} />
				<Route path="products" element={<ProductsPage />} />
				<Route path="tenant" element={<TenantPage />} />
				<Route path="reviews" element={<ReviewsPage />} />
				<Route path="account" element={<AccountPage />} />
			</Route>
			<Route
				path="/admin"
				element={
					<RequirePlatformAdmin>
						<AdminLayout />
					</RequirePlatformAdmin>
				}
			>
				<Route index element={<Navigate to="tenants" replace />} />
				<Route path="tenants" element={<AdminTenantsPage />} />
				<Route path="usage" element={<AdminUsagePage />} />
				<Route path="support-reports" element={<AdminSupportReportsPage />} />
			</Route>
		</Routes>
	);
}
