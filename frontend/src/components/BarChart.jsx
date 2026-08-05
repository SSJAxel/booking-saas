const WIDTH = 640;
const HEIGHT = 180;
const PADDING_BOTTOM = 22;
const BAR_RADIUS = 4;

/**
 * Deliberately minimal — no chart library (see README "Stack": plain fetch, no UI kit). One
 * series per chart, so no legend is needed (the title above it names the series) — see the
 * dataviz skill's "single series needs no legend box" rule. Hover tooltip uses a native SVG
 * <title> instead of custom JS state, since that's the cheapest correct way to get per-bar exact
 * values without building a tooltip layer.
 */
export default function BarChart({ data, color, valueFormatter = (v) => String(v), ariaLabel }) {
	const max = Math.max(1, ...data.map((d) => d.value));
	const plotHeight = HEIGHT - PADDING_BOTTOM;
	const barWidth = WIDTH / data.length;
	const barGap = Math.min(18, barWidth * 0.25);

	return (
		<svg
			viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
			className="bar-chart"
			role="img"
			aria-label={ariaLabel ?? data.map((d) => `${d.label}: ${valueFormatter(d.value)}`).join(", ")}
		>
			<line x1="0" y1={plotHeight} x2={WIDTH} y2={plotHeight} className="bar-chart-baseline" />
			{data.map((d, i) => {
				const barHeight = (d.value / max) * (plotHeight - 12);
				const x = i * barWidth + barGap / 2;
				const w = barWidth - barGap;
				const y = plotHeight - barHeight;
				return (
					<g key={d.label + i}>
						<title>
							{d.label}: {valueFormatter(d.value)}
						</title>
						<path
							d={roundedTopBarPath(x, y, w, barHeight, Math.min(BAR_RADIUS, w / 2))}
							fill={color}
						/>
						<text x={x + w / 2} y={HEIGHT - 6} textAnchor="middle" className="bar-chart-label">
							{d.label}
						</text>
					</g>
				);
			})}
		</svg>
	);
}

function roundedTopBarPath(x, y, w, h, r) {
	if (h <= 0) return "";
	if (h < r) r = h;
	return `M${x},${y + h} L${x},${y + r} Q${x},${y} ${x + r},${y} L${x + w - r},${y} Q${x + w},${y} ${x + w},${y + r} L${x + w},${y + h} Z`;
}
