import { useEffect, useRef, useState } from 'react';
import { request } from '../api/client';

export const SIMULATION_POLL_MS = 250;

type Coordinate = [number, number];
type Status = 'READY' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'TIMEOUT' | 'FAILED';
type Command = 'START' | 'PAUSE' | 'RESUME' | 'RESET' | 'SET_PLAYBACK_RATE';
export interface SimulationSnapshot {
  id: string;
  status: Status;
  simulationTimeSeconds: number;
  playbackRate: number;
  routeGeometry: Coordinate[];
  routeCumulativeMeters: number[];
  navigation: {
    currentStreet: string;
    upcoming: {
      type: 'SIGNAL' | 'JUNCTION';
      nextStreet: string;
      distanceM: number;
      estimatedSeconds: number | null;
      phase: 'GREEN' | 'YELLOW' | 'RED' | null;
      remainingPhaseSeconds: number | null;
    } | null;
  };
  vehicle: { position: Coordinate; speedMps: number };
  signals: { id: string; position: Coordinate; phase: 'GREEN' | 'YELLOW' | 'RED'; type: string }[];
  metrics: { distanceM: number; stopCount: number; stoppedTimeSeconds: number };
}

interface Props {
  routeKey: string;
  networkId: string;
  start: string;
  destination: string;
  driving: boolean;
  openDriving: () => void;
  publish: (result: { key: string; snapshot?: SimulationSnapshot }) => void;
}

const simulationPath = (id: string) => '/simulations/' + encodeURIComponent(id);
const dispose = (id: string) => { void request(simulationPath(id), { method: 'DELETE', keepalive: true }).catch(() => undefined); };
const labels: Record<Status, string> = {
  READY: 'Gotowy', RUNNING: 'Przejazd trwa', PAUSED: 'Pauza', COMPLETED: 'Dojechano do celu',
  TIMEOUT: 'Przekroczono limit czasu symulacji', FAILED: 'Symulacja została przerwana',
};

export function BaselineControls({ routeKey, networkId, start, destination, publish, driving, openDriving }: Props) {
  const [snapshot, setSnapshot] = useState<SimulationSnapshot>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(false);
  const session = useRef<string | undefined>(undefined);
  const polling = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      publish({ key: routeKey });
      if (session.current) dispose(session.current);
    };
  }, [routeKey, publish]);

  const id = snapshot?.id;
  const status = snapshot?.status;
  useEffect(() => {
    if (!id || status !== 'RUNNING' || busy) return;
    const controller = new AbortController();
    polling.current = controller;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      if (!id || controller.signal.aborted) return;
      try {
        const next = await request<SimulationSnapshot>(simulationPath(id), { signal: controller.signal });
        if (controller.signal.aborted) return;
        setError('');
        setSnapshot(next);
        publish({ key: routeKey, snapshot: next });
      } catch {
        if (!controller.signal.aborted) setError('Nie udało się odświeżyć symulacji. Ponawiam…');
      }
      if (!controller.signal.aborted) timer = setTimeout(() => void poll(), SIMULATION_POLL_MS);
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [id, status, busy, publish, routeKey]);

  async function send(type: Command, playbackRate?: number) {
    if (busy) return;
    polling.current?.abort();
    setBusy(true);
    setError('');
    try {
      let id = session.current;
      if (!id) {
        const created = await request<SimulationSnapshot>('/simulations', {
          method: 'POST', body: JSON.stringify({ networkId, startNodeId: start, destinationNodeId: destination, randomSeed: 42 }),
        });
        if (!mounted.current) { dispose(created.id); return; }
        id = session.current = created.id;
        setSnapshot(created);
        publish({ key: routeKey, snapshot: created });
      }
      const next = await request<SimulationSnapshot>(simulationPath(id) + '/commands', {
        method: 'POST', body: JSON.stringify({ commandId: crypto.randomUUID(), type, playbackRate }),
      });
      if (!mounted.current) return;
      setSnapshot(next);
      publish({ key: routeKey, snapshot: next });
      if (type === 'START') openDriving();
    } catch {
      if (mounted.current) setError('Nie udało się wykonać polecenia symulacji. Spróbuj ponownie.');
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  return <div className="baseline-controls" aria-label="Symulacja przejazdu">
    <strong>Symulacja · A*</strong>
    <div className="baseline-actions">
      {(!status || status === 'READY') && <button disabled={busy} onClick={() => void send('START')}>Rozpocznij teraz</button>}
      {status === 'RUNNING' && <button disabled={busy} onClick={() => void send('PAUSE')}>Pauza</button>}
      {status === 'PAUSED' && <button disabled={busy} onClick={() => void send('RESUME')}>Wznów</button>}
      {snapshot && status !== 'READY' && !driving && <button type="button" onClick={openDriving}>Widok jazdy</button>}
      {snapshot && <button disabled={busy} onClick={() => void send('RESET')}>Reset</button>}
      {snapshot && <label>Tempo <select disabled={busy} value={snapshot.playbackRate}
        onChange={event => void send('SET_PLAYBACK_RATE', Number(event.target.value))}>
        {[0.5, 1, 2, 5].map(rate => <option key={rate} value={rate}>{rate}×</option>)}
      </select></label>}
    </div>
    {snapshot && <>
      <p role="status">{labels[snapshot.status]}</p>
      <p>Czas: {snapshot.simulationTimeSeconds.toFixed(1)} s · Prędkość: {Math.round(snapshot.vehicle.speedMps * 3.6)} km/h</p>
      <p>Zatrzymania: {snapshot.metrics.stopCount} · Postój: {snapshot.metrics.stoppedTimeSeconds.toFixed(1)} s</p>
    </>}
    {error && <p role="alert">{error}</p>}
  </div>;
}
