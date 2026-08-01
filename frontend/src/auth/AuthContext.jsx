import { createContext, useCallback, useContext, useState } from "react";
import { api } from "../api.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
	const [session, setSession] = useState(() => {
		const raw = localStorage.getItem("session");
		return raw ? JSON.parse(raw) : null;
	});

	function persist(res) {
		localStorage.setItem("token", res.token);
		localStorage.setItem("session", JSON.stringify(res));
		setSession(res);
	}

	const login = useCallback(async (body) => {
		persist(await api.login(body));
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
		<AuthContext.Provider value={{ session, login, register, verifyEmail, logout }}>{children}</AuthContext.Provider>
	);
}

export function useAuth() {
	return useContext(AuthContext);
}
