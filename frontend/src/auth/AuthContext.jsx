import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api } from "../api.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
	const [session, setSession] = useState(() => {
		const raw = localStorage.getItem("session");
		return raw ? JSON.parse(raw) : null;
	});
	const [me, setMe] = useState(null);

	function persist(res) {
		localStorage.setItem("token", res.token);
		localStorage.setItem("session", JSON.stringify(res));
		setSession(res);
	}

	const refreshMe = useCallback(async () => {
		try {
			setMe(await api.me.get());
		} catch {
			setMe(null);
		}
	}, []);

	// Personal profile (display name, avatar) shown in the sidebar — kept here, not in
	// DashboardLayout's own state, so AccountPage's refreshMe() after a save updates the sidebar
	// too instead of only refreshing on the next full navigation.
	useEffect(() => {
		if (session) refreshMe();
		else setMe(null);
	}, [session, refreshMe]);

	const login = useCallback(async (body) => {
		const res = await api.login(body);
		persist(res);
		return res;
	}, []);

	const register = useCallback(async (body) => {
		// No token comes back — the account needs email verification before it can log in.
		return api.register(body);
	}, []);

	const verifyEmail = useCallback(async (body) => {
		persist(await api.verifyEmail(body));
	}, []);

	const logout = useCallback(() => {
		localStorage.removeItem("token");
		localStorage.removeItem("session");
		setSession(null);
	}, []);

	return (
		<AuthContext.Provider value={{ session, me, refreshMe, login, register, verifyEmail, logout }}>
			{children}
		</AuthContext.Provider>
	);
}

export function useAuth() {
	return useContext(AuthContext);
}
