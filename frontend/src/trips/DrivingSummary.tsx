import { algorithmNames, queueLabels, type QueueMotion, type Algorithm, type SimulationSnapshot } from './ComparisonControls';

const phaseLabels = { GREEN: 'Zielone', YELLOW: 'Żółte', RED: 'Czerwone' };

export function DrivingSummary({ navigation, close, algorithm, onFollow, greenApproach, speedMps, coasting, queueMotion }: {
  navigation: SimulationSnapshot['navigation']; close: () => void;
  algorithm: Algorithm; onFollow: (value: Algorithm) => void;
  greenApproach: SimulationSnapshot['greenApproach']; speedMps: number; coasting?: boolean; queueMotion?: QueueMotion | null;
}) {
  const upcoming = navigation?.upcoming;
  return <div className="driving-summary">
    <div className="driving-heading">
      <div><span>Jedziesz ulicą</span><strong>{navigation?.currentStreet || 'Droga bez nazwy'}</strong></div>
      <button type="button" autoFocus onClick={close} aria-label="Zamknij widok jazdy">×</button>
    </div>
    <div className="follow-options" role="group" aria-label="Śledzony pojazd">
      {(['ASTAR', 'GREENWAVE'] as const).map(value => <button key={value} type="button"
        aria-pressed={algorithm === value} onClick={() => onFollow(value)}>{algorithmNames[value]}</button>)}
    </div>
    {queueMotion && <div className="driving-next"><strong>Korek · {queueLabels[queueMotion]}</strong></div>}
    {algorithm === 'GREENWAVE' && coasting && <div className="driving-next">
      <strong>Toczenie do świateł</strong><span>Odcięcie wtrysku · 0 l/h</span>
    </div>}
    {algorithm === 'GREENWAVE' && greenApproach && <div className="driving-next">
      <strong>{greenApproach.targetSpeedMps > speedMps + 0.25 ? 'Przyspiesz do' : 'Cel na zielone'} {Math.round(greenApproach.targetSpeedMps * 3.6)} km/h</strong>
      <span>Przejazd za ok. {Math.ceil(greenApproach.estimatedCrossingSeconds)} s · zielone jeszcze {Math.ceil(greenApproach.greenRemainingSeconds)} s</span>
    </div>}
    {upcoming ? <div className="driving-next">
      <strong>{upcoming.type === 'SIGNAL' ? 'Najbliższe światła' : 'Najbliższe skrzyżowanie'}</strong>
      {upcoming.nextStreet && <span>{upcoming.nextStreet}</span>}
      <div className="driving-readouts">
        <span>{Math.round(upcoming.distanceM)} m</span>
        <span>{upcoming.estimatedSeconds === null ? 'Brak szacunku dojazdu'
          : `ok. ${Math.ceil(upcoming.estimatedSeconds)} s do dojazdu`}</span>
      </div>
      {upcoming.phase && <span className="driving-phase">
        <i className={`phase-${upcoming.phase.toLowerCase()}`} aria-hidden="true" />
        {phaseLabels[upcoming.phase]}
        {upcoming.remainingPhaseSeconds !== null && ` · zmiana za ${Math.ceil(upcoming.remainingPhaseSeconds)} s`}
      </span>}
    </div> : <p>Brak kolejnych skrzyżowań na trasie.</p>}
  </div>;
}
