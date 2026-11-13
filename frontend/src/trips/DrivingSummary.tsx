import { queueLabels, type SimulationSnapshot } from './ComparisonControls';

const phaseLabels = { GREEN: 'Zielone', YELLOW: 'Żółte', RED: 'Czerwone' };

export function DrivingSummary({ run }: { run: SimulationSnapshot }) {
  const upcoming = run.navigation?.upcoming;
  const arrived = run.status === 'COMPLETED';
  const roadLimit = run.navigation?.roadSpeedLimitMps;
  const limitKmh = roadLimit == null ? undefined : Math.round(roadLimit * 3.6);
  const greenApproach = run.greenApproach;
  return <aside className="driving-banner" aria-label="Nawigacja">
    <span className="driving-direction" aria-hidden="true">{arrived ? '✓' : '↑'}</span>
    <div className="driving-instructions">
      <span className="driving-caption">{arrived ? 'Przejazd zakończony' : 'Jedziesz ulicą'}</span>
      <strong className="driving-street">{run.navigation?.currentStreet || 'Droga bez nazwy'}</strong>
      {upcoming ? <div className="driving-next">
        <strong>{upcoming.type === 'SIGNAL' ? 'Następne światła' : 'Następne skrzyżowanie'}
          {upcoming.nextStreet && ` · ${upcoming.nextStreet}`}</strong>
        <div className="driving-readouts">
          <span>{Math.round(upcoming.distanceM)} m
            {upcoming.estimatedSeconds != null && ` · ok. ${Math.ceil(upcoming.estimatedSeconds)} s`}</span>
          {upcoming.phase && <span className="driving-phase">
            <i className={`phase-${upcoming.phase.toLowerCase()}`} aria-hidden="true" />
            {phaseLabels[upcoming.phase]}
            {upcoming.remainingPhaseSeconds != null && ` · zmiana za ${Math.ceil(upcoming.remainingPhaseSeconds)} s`}
          </span>}
        </div>
      </div> : <span className="driving-caption">{arrived ? 'Na miejscu' : 'Brak kolejnych skrzyżowań na trasie'}</span>}
      {run.queueMotion && <span className="driving-caption">Korek · {queueLabels[run.queueMotion]}</span>}
      {run.coasting && <span className="driving-caption">Toczenie do świateł · 0 l/h</span>}
      {greenApproach && <span className="driving-caption">
        {greenApproach.targetSpeedMps > run.vehicle.speedMps + 0.25 ? 'Przyspiesz do' : 'Cel na zielone'}
        {' '}{Math.round(greenApproach.targetSpeedMps * 3.6)} km/h · przejazd za ok. {Math.ceil(greenApproach.estimatedCrossingSeconds)} s
        {' · zielone jeszcze '}{Math.ceil(greenApproach.greenRemainingSeconds)} s
      </span>}
    </div>
    {limitKmh !== undefined && <div className="driving-speed-limit" role="img"
      aria-label={`Limit prędkości na drodze ${limitKmh} km/h`}>
      <strong>{limitKmh}</strong><span>km/h</span>
    </div>}
  </aside>;
}
