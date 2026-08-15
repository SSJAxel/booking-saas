/** Shared inline-SVG icon set for the panel — same convention as the icons already defined locally
 * in DashboardLayout.jsx/DashboardHomePage.jsx/BookingPage.jsx (stroke-based, 24x24 viewBox,
 * currentColor, aria-hidden), centralized here only because these are each used from more than one
 * page. No icon library/dependency — plain components so they inherit color/size from CSS like any
 * other inline SVG in this codebase. */

export function HomeIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M4 11.5 12 4l8 7.5M6 9.5V19a1 1 0 0 0 1 1h4v-5h2v5h4a1 1 0 0 0 1-1V9.5"
				stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function CalendarIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<rect x="3" y="4" width="18" height="18" rx="3" stroke="currentColor" strokeWidth="1.8" />
			<path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
		</svg>
	);
}

export function BranchIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M12 21s7-6.6 7-12a7 7 0 1 0-14 0c0 5.4 7 12 7 12Z"
				stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			<circle cx="12" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.8" />
		</svg>
	);
}

export function TeamIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<circle cx="9" cy="8" r="3.2" stroke="currentColor" strokeWidth="1.8" />
			<path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
			<path d="M15.5 5.2a3.2 3.2 0 0 1 0 6.2M18.5 20c0-2.9-2-5.3-4.7-5.9"
				stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
		</svg>
	);
}

export function ScissorsIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<circle cx="6" cy="6" r="2.4" stroke="currentColor" strokeWidth="1.8" />
			<circle cx="6" cy="18" r="2.4" stroke="currentColor" strokeWidth="1.8" />
			<path d="m20 5-12.2 6.5M20 19 7.8 12.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
		</svg>
	);
}

export function BoxIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5v-9Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
			<path d="M3.5 7.5 12 12m0 0 8.5-4.5M12 12v9" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
		</svg>
	);
}

export function StarIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="m12 3 2.7 5.9 6.3.7-4.7 4.4 1.2 6.3L12 17.3 6.5 20.3l1.2-6.3-4.7-4.4 6.3-.7L12 3Z"
				stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
		</svg>
	);
}

export function PlusIcon(props) {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
		</svg>
	);
}

export function EditIcon(props) {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function TrashIcon(props) {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M4 7h16M9 7V4.8c0-.4.4-.8.9-.8h4.2c.5 0 .9.4.9.8V7M6.5 7l.8 12c.1 1.1 1 2 2.1 2h5.2c1.1 0 2-.9 2.1-2l.8-12"
				stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function EyeIcon(props) {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
			<circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
		</svg>
	);
}

export function EyeOffIcon(props) {
	return (
		<svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M3 3l18 18M10.6 5.2C11 5.1 11.5 5 12 5c7 0 10.5 7 10.5 7-.6 1.2-1.7 2.9-3.4 4.3M6.6 6.6C3.9 8.3 1.5 12 1.5 12s3.5 7 10.5 7c1.4 0 2.7-.3 3.8-.7"
				stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function InboxIcon(props) {
	return (
		<svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M3.5 12.5h5l1.5 2.5h4l1.5-2.5h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
			<path d="M5.2 5.5h13.6a1 1 0 0 1 .97.76l1.63 6.5a1 1 0 0 1-.03.56v4.68a1 1 0 0 1-1 1H3.63a1 1 0 0 1-1-1v-4.68a1 1 0 0 1-.03-.56l1.63-6.5a1 1 0 0 1 .97-.76Z"
				stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function StoreIcon(props) {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="M4 9.5 5.2 4h13.6L20 9.5M4 9.5a2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0M5 9.5V20h14V9.5"
				stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function MailIcon(props) {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<rect x="3" y="5" width="18" height="14" rx="2.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="m4 6.5 8 6.5 8-6.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function LayersIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<path d="m12 3 9 5-9 5-9-5 9-5Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			<path d="m3 13 9 5 9-5M3 8l9 5 9-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function UserIcon(props) {
	return (
		<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<circle cx="12" cy="8" r="3.6" stroke="currentColor" strokeWidth="1.8" />
			<path d="M4.5 20c0-4.1 3.4-7.5 7.5-7.5s7.5 3.4 7.5 7.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
		</svg>
	);
}

export function LockIcon(props) {
	return (
		<svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true" {...props}>
			<rect x="4.5" y="10.5" width="15" height="10" rx="2.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}
