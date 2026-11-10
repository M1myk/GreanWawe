import { StrictMode, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { RoadMap } from './trips/RoadMap';
import { TripPresets } from './trips/TripPresets';
import { defaultConfig } from './trips/types';

function App() {
  const [endpoints, setEndpoints] = useState({ startNodeId: '', destinationNodeId: '' });
  const [showSaved, setShowSaved] = useState(false);
  const saved = useRef<HTMLDialogElement>(null);

  return <main className="map-screen">
    <RoadMap networkId={defaultConfig.scenarioId} start={endpoints.startNodeId}
      destination={endpoints.destinationNodeId} fullScreen
      onSelect={(field, id) => setEndpoints(current => ({ ...current, [field]: id }))}
      openSaved={() => { saved.current?.showModal(); setShowSaved(true); }} />
    <dialog ref={saved} className="saved-dialog" aria-labelledby="saved-title" onClose={() => setShowSaved(false)}>
      <div className="saved-heading">
        <h2 id="saved-title">Zapisane przejazdy</h2>
        <button type="button" onClick={() => saved.current?.close()}>Zamknij</button>
      </div>
      {showSaved && <TripPresets />}
    </dialog>
  </main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
