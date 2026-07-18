export function PulseBackground() {
  return (
    <div
      className="fixed inset-0 -z-10 overflow-hidden pointer-events-none select-none"
      style={{ opacity: 0.7 }}
      aria-hidden
    >
      <div className="absolute inset-0 flex items-center justify-center">
        {[0, 0.75, 1.5].map((delay) => (
          <div
            key={delay}
            className="absolute"
            style={{
              filter: 'blur(24px)',
              animation: `heartPulseRing 2.4s cubic-bezier(0.2, 0.8, 0.4, 1) ${delay}s infinite`,
            }}
          >
            <svg width="220" height="220" viewBox="0 0 100 100" className="text-accent-500">
              <circle cx="50" cy="50" r="46" fill="currentColor" />
            </svg>
          </div>
        ))}
      </div>
      <svg
        className="absolute text-accent-500"
        style={{
          bottom: 72,
          left: 0,
          width: '200%',
          height: 80,
          opacity: 0.19,
          animation: 'ecgScroll 8s linear infinite',
        }}
        viewBox="0 0 800 80"
      >
        <path
          d={
            'M0,40 L35,40 L40,35 L45,40 L60,40 L64,46 L68,5 L72,65 L76,40 L90,40 L97,32 L104,40 L140,40 L200,40 ' +
            'M200,40 L235,40 L240,35 L245,40 L260,40 L264,46 L268,5 L272,65 L276,40 L290,40 L297,32 L304,40 L340,40 L400,40 ' +
            'M400,40 L435,40 L440,35 L445,40 L460,40 L464,46 L468,5 L472,65 L476,40 L490,40 L497,32 L504,40 L540,40 L600,40 ' +
            'M600,40 L635,40 L640,35 L645,40 L660,40 L664,46 L668,5 L672,65 L676,40 L690,40 L697,32 L704,40 L740,40 L800,40'
          }
          stroke="currentColor"
          strokeWidth="1.5"
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  )
}
