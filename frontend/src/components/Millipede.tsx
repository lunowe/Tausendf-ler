/** Wordmark glyph: a segmented body with legs – the crawler as a technical drawing. */
export function Millipede({ className = "" }: { className?: string }) {
  const segments = [0, 1, 2, 3, 4, 5];
  return (
    <svg
      viewBox="0 0 96 34"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="1"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M6 17 H90" strokeDasharray="2 3" opacity="0.45" />
      {segments.map((i) => {
        const x = 12 + i * 14;
        return (
          <g key={i}>
            <circle cx={x} cy={17} r={5.5} opacity={0.9} />
            <path d={`M${x - 2.5} 22.5 L${x - 5} 29`} opacity="0.7" />
            <path d={`M${x + 2.5} 22.5 L${x + 5} 29`} opacity="0.7" />
            <path d={`M${x - 2.5} 11.5 L${x - 5} 5`} opacity="0.7" />
            <path d={`M${x + 2.5} 11.5 L${x + 5} 5`} opacity="0.7" />
          </g>
        );
      })}
      <circle cx="12" cy="17" r="1.6" fill="currentColor" stroke="none" />
    </svg>
  );
}
