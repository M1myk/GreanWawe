import { StrictMode, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { RoadMap } from './trips/RoadMap';
import { TripPresets } from './trips/TripPresets';
import { defaultConfig, type TripConfig } from './trips/types';

function App() {
  const [config, setConfig] = useState<TripConfig>(defaultConfig);
  const [networkId, setNetworkId] = useState(defaultConfig.scenarioId);
  const [showSaved, setShowSaved] = useState(false);
  const saved = useRef<HTMLDialogElement>(null);

  return <main className="map-screen">
    <RoadMap key={networkId} networkId={networkId} start={config.startNodeId}
      destination={config.destinationNodeId} comparisonConfig={config} fullScreen
      onSelect={(field, id) => setConfig(current => ({ ...current, [field]: id }))}
      loadExample={() => {
        setNetworkId(defaultConfig.scenarioId);
        setConfig({ ...defaultConfig, startNodeId: '1319985144', destinationNodeId: '11724815621', trafficMode: 'NORMAL' });
      }}
      onTrafficMode={trafficMode => setConfig(current => ({ ...current, trafficMode }))}
      resetMap={networkId === defaultConfig.scenarioId ? undefined : () => {
        setConfig(defaultConfig);
        setNetworkId(defaultConfig.scenarioId);
      }}
      openSaved={() => { saved.current?.showModal(); setShowSaved(true); }} />
    <dialog ref={saved} className="saved-dialog" aria-labelledby="saved-title" onClose={() => setShowSaved(false)}>
      <div className="saved-heading">
        <h2 id="saved-title">Zapisane przejazdy</h2>
        <button type="button" onClick={() => saved.current?.close()}>Zamknij</button>
      </div>
      {showSaved && <TripPresets onLaunch={(savedConfig, savedNetwork) => {
        setConfig(savedConfig);
        setNetworkId(savedNetwork);
        saved.current?.close();
      }} />}
    </dialog>
  </main>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
