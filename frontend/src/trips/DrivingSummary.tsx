import type { SimulationSnapshot } from './BaselineControls';

const phaseLabels = { GREEN: 'Zielone', YELLOW: 'Żółte', RED: 'Czerwone' };

export function DrivingSummary({ navigation, close }: { navigation: SimulationSnapshot['navigation']; close: () => void }) {
  const upcoming = navigation?.upcoming;
  return <div className="driving-summary">
    <div className="driving-heading">
      <div><span>Jedziesz ulicą</span><strong>{navigation?.currentStreet || 'Droga bez nazwy'}</strong></div>
      <button type="button" autoFocus onClick={close} aria-label="Zamknij widok jazdy">×</button>
    </div>
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
