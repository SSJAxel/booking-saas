const STORAGE_KEY = "theme";

export function getStoredTheme() {
	return localStorage.getItem(STORAGE_KEY);
}

/** null clears the override and falls back to the OS-level prefers-color-scheme. */
export function setTheme(theme) {
	if (theme === null) {
		localStorage.removeItem(STORAGE_KEY);
		document.documentElement.removeAttribute("data-theme");
		return;
	}
	localStorage.setItem(STORAGE_KEY, theme);
	document.documentElement.setAttribute("data-theme", theme);
}

export function systemPrefersDark() {
	return window.matchMedia("(prefers-color-scheme: dark)").matches;
}
