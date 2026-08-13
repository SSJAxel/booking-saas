export default function StatTile({ icon, iconBg, iconColor, value, label, title }) {
	return (
		<div className="card stat-tile" title={title}>
			<div className="stat-tile-header">
				<span className="stat-icon" style={{ background: iconBg, color: iconColor }}>
					{icon}
				</span>
				<span className="muted">{label}</span>
			</div>
			<span className="stat-value" style={{ color: iconColor }}>
				{value}
			</span>
		</div>
	);
}
