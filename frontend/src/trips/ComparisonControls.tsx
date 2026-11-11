import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, request } from '../api/client';
import { trafficLabels, type TripConfig } from './types';

export const SIMULATION_POLL_MS = 250;

type Coordinate = [number, number];
export type Algorithm = 'ASTAR' | 'GREENWAVE';
type Status = 'READY' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'TIMEOUT' | 'FAILED';
type Command = 'START' | 'PAUSE' | 'RESUME' | 'RESET' | 'SET_PLAYBACK_RATE';
export interface SimulationSnapshot {
  id: string;
  algorithm: Algorithm;
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
  coasting?: boolean;
  fuelFlowLitersPerHour?: number | null;
  routeCandidates?: number | null;
  greenApproach?: { targetSpeedMps: number; estimatedCrossingSeconds: number; greenRemainingSeconds: number } | null;
  vehicle: { position: Coordinate; speedMps: number };
  signals: { id: string; position: Coordinate; phase: 'GREEN' | 'YELLOW' | 'RED'; type: string }[];
  metrics: { distanceM: number; stopCount: number; stoppedTimeSeconds: number;
    travelTimeSeconds: number | null; speedVariationMps: number; fuelLiters?: number | null };
}


export interface ComparisonSnapshot {
  id: string;
  revision: number;
  status: Exclude<Status, 'TIMEOUT'>;
  simulationTimeSeconds: number;
  playbackRate: number;
  runs: [SimulationSnapshot, SimulationSnapshot];
  timeSavedSeconds: number | null;
  timeSavedPercent: number | null;
  failure: { code: string; message: string } | null;
}

interface Props {
  routeKey: string;
  config: TripConfig;
  driving: boolean;
  openDriving: () => void;
  publish: (result: { key: string; snapshot?: ComparisonSnapshot }) => void;
}

export const algorithmNames: Record<Algorithm, string> = { ASTAR: 'A*', GREENWAVE: 'GreenWave' };
const pathFor = (id: string) => '/comparisons/' + encodeURIComponent(id);
const release = (id: string) => { void request(pathFor(id), { method: 'DELETE', keepalive: true }).catch(() => undefined); };
const labels: Record<Status, string> = {
  READY: 'Gotowy', RUNNING: 'W trakcie', PAUSED: 'Pauza', COMPLETED: 'Dojechał',
  TIMEOUT: 'Limit czasu', FAILED: 'Przerwany',
};
const comparisonLabels = { ...labels, COMPLETED: 'Porównanie zakończone' };
const seconds = (value: number | null) => value === null ? '—' : value.toLocaleString('pl', { maximumFractionDigits: 1 }) + ' s';

export function ComparisonControls({ routeKey, config, publish, driving, openDriving }: Props) {
  const [snapshot, setSnapshot] = useState<ComparisonSnapshot>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(false);
  const latest = useRef<ComparisonSnapshot | undefined>(undefined);
  const polling = useRef<AbortController | undefined>(undefined);
  const sending = useRef(false);

  const accept = useCallback((next: ComparisonSnapshot) => {
    if (!mounted.current) return;
    const restarted = next.failure?.code === 'BACKEND_RESTARTED';
    if (!restarted && latest.current && next.revision < latest.current.revision) return;
    latest.current = next;
    setSnapshot(next);
    publish({ key: routeKey, snapshot: next });
  }, [publish, routeKey]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      publish({ key: routeKey });
      if (latest.current) release(latest.current.id);
    };
  }, [routeKey, publish]);

  const id = snapshot?.id;
  const status = snapshot?.status;
  useEffect(() => {
    if (!id || (status !== 'RUNNING' && status !== 'PAUSED') || busy) return;
    const endpoint = pathFor(id);
    const controller = new AbortController();
    polling.current = controller;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const next = await request<ComparisonSnapshot>(endpoint, { signal: controller.signal });
        if (controller.signal.aborted) return;
        accept(next);
        setError('');
      } catch (reason) {
        if (controller.signal.aborted) return;
        if (reason instanceof ApiError && reason.status === 404) {
          latest.current = undefined;
          setSnapshot(undefined);
          publish({ key: routeKey });
          setError('Sesja wygasła. Rozpocznij nowe porównanie.');
          return;
        }
        setError('Brak aktualnych danych z symulacji. Ponawiam…');
      }
      if (!controller.signal.aborted) timer = setTimeout(() => void poll(), status === 'PAUSED' ? 2000 : SIMULATION_POLL_MS);
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [id, status, busy, accept, publish, routeKey]);

  async function send(type: Command, playbackRate?: number) {
    if (sending.current) return;
    sending.current = true;
    polling.current?.abort();
    setBusy(true);
    setError('');
    let current = latest.current;
    try {
      if (!current) {
        current = await request<ComparisonSnapshot>('/comparisons', { method: 'POST', body: JSON.stringify(config) });
        if (!mounted.current) { release(current.id); return; }
        accept(current);
      }
      const endpoint = pathFor(current.id) + '/commands';
      const body = JSON.stringify({ commandId: crypto.randomUUID(), expectedRevision: current.revision, type, playbackRate });
      let next: ComparisonSnapshot;
      try {
        next = await request<ComparisonSnapshot>(endpoint, { method: 'POST', body });
      } catch (reason) {
        if (!mounted.current || !(reason instanceof ApiError) || reason.status !== 0) throw reason;
        next = await request<ComparisonSnapshot>(endpoint, { method: 'POST', body });
      }
      if (!mounted.current) return;
      accept(next);
      if (type === 'START') openDriving();
    } catch (reason) {
      if (!mounted.current) return;
      if (current && reason instanceof ApiError && reason.status === 409) {
        const refreshed = await request<ComparisonSnapshot>(pathFor(current.id)).catch(() => undefined);
        if (refreshed) accept(refreshed);
        if (mounted.current) setError('Stan porównania się zmienił. Sprawdź go i ponów polecenie.');
      } else if (reason instanceof ApiError && reason.status === 404) {
        latest.current = undefined;
        setSnapshot(undefined);
        publish({ key: routeKey });
        setError('Sesja wygasła. Rozpocznij nowe porównanie.');
      } else {
        setError(reason instanceof ApiError && reason.status === 422
          ? reason.fields.map(field => field.message).join(' ') || reason.message
          : 'Nie udało się wykonać polecenia. Spróbuj ponownie.');
      }
    } finally {
      sending.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  const terminal = status === 'COMPLETED' || status === 'FAILED';
  return <div className="baseline-controls" aria-label="Porównanie przejazdów">
    <strong>A* i GreenWave</strong>
    <div className="baseline-actions">
      {(!status || status === 'READY') && <button disabled={busy} onClick={() => void send('START')}>Rozpocznij teraz</button>}
      {status === 'RUNNING' && <button disabled={busy} onClick={() => void send('PAUSE')}>Pauza</button>}
      {status === 'PAUSED' && <button disabled={busy} onClick={() => void send('RESUME')}>Wznów</button>}
      {snapshot && status !== 'READY' && !driving && <button type="button" onClick={openDriving}>Widok jazdy</button>}
      {snapshot && <button disabled={busy} onClick={() => void send('RESET')}>Reset</button>}
      {snapshot && <label>Tempo <select disabled={busy || terminal} value={snapshot.playbackRate}
        onChange={event => void send('SET_PLAYBACK_RATE', Number(event.target.value))}>
        {[0.5, 1, 2, 5].map(rate => <option key={rate} value={rate}>{rate}×</option>)}
      </select></label>}
    </div>
    {snapshot && <>
      <p role="status">{comparisonLabels[snapshot.status]}</p>
      <p>Ruch · {trafficLabels[config.trafficMode ?? 'NONE']}</p>
      <p>Czas symulacji {seconds(snapshot.simulationTimeSeconds)}</p>
      <ComparisonMetrics snapshot={snapshot} />
      {snapshot.runs[1].coasting && <p>GreenWave · toczenie bez wtrysku paliwa</p>}
      {(snapshot.runs[1].routeCandidates ?? 0) > 0 && <p>Warianty trasy GreenWave · {snapshot.runs[1].routeCandidates}</p>}
      {snapshot.runs.some(run => run.metrics.fuelLiters != null) &&
        <p className="note">Szacowane zużycie benzyny · Mazda 3 2.0</p>}
      {terminal && <p className="comparison-result">{resultText(snapshot)}</p>}
      {snapshot.failure && <p role="alert">{snapshot.failure.message}</p>}
    </>}
    {error && <p role="alert">{error}</p>}
  </div>;
}

function ComparisonMetrics({ snapshot }: { snapshot: ComparisonSnapshot }) {
  const rows: Array<[string, (run: SimulationSnapshot) => string | number]> = [
    ['Stan', run => labels[run.status]],
    ['Prędkość', run => Math.round(run.vehicle.speedMps * 3.6) + ' km/h'],
    ['Cel na zielone', run => run.greenApproach
      ? Math.round(run.greenApproach.targetSpeedMps * 3.6) + ' km/h' : '—'],
    ['Czas dojazdu', run => seconds(run.metrics.travelTimeSeconds)],
    ['Chwilowe zużycie', run => run.fuelFlowLitersPerHour == null ? '—'
      : run.fuelFlowLitersPerHour.toLocaleString('pl', { maximumFractionDigits: 1 }) + ' l/h'],
    ['Benzyna', run => run.metrics.fuelLiters == null ? '—'
      : (run.metrics.fuelLiters * 1000).toLocaleString('pl', { maximumFractionDigits: 1 }) + ' ml'],
    ['Śr. spalanie', run => run.metrics.fuelLiters == null || run.metrics.distanceM < 10 ? '—'
      : (run.metrics.fuelLiters * 100000 / run.metrics.distanceM).toLocaleString('pl', { maximumFractionDigits: 1 }) + ' l/100 km'],
    ['Dystans', run => (run.metrics.distanceM / 1000).toLocaleString('pl', { maximumFractionDigits: 2 }) + ' km'],
    ['Zatrzymania', run => run.metrics.stopCount],
    ['Postój', run => seconds(run.metrics.stoppedTimeSeconds)],
    ['Zmiany prędkości', run => run.metrics.speedVariationMps.toLocaleString('pl', { maximumFractionDigits: 1 }) + ' m/s'],
  ];
  return <table className="comparison-metrics" aria-label="Wyniki porównania">
    <thead><tr><th scope="col">Metryka</th>{snapshot.runs.map(run =>
      <th key={run.algorithm} scope="col"><span className={'run-key ' + run.algorithm.toLowerCase()} />{algorithmNames[run.algorithm]}</th>)}</tr></thead>
    <tbody>{rows.map(([label, value]) => <tr key={label}><th scope="row">{label}</th>
      {snapshot.runs.map(run => <td key={run.algorithm}>{value(run)}</td>)}</tr>)}</tbody>
  </table>;
}

function resultText(snapshot: ComparisonSnapshot) {
  const difference = snapshot.timeSavedSeconds;
  if (difference === null) return 'Brak porównywalnego czasu dojazdu.';
  if (Math.abs(difference) < 0.05) return 'Oba auta dojechały w tym samym czasie.';
  const percent = snapshot.timeSavedPercent === null ? '' : ' (' + Math.abs(snapshot.timeSavedPercent)
    .toLocaleString('pl', { maximumFractionDigits: 1 }) + '%)';
  return 'GreenWave ' + (difference > 0 ? 'szybciej' : 'wolniej') + ' o ' + seconds(Math.abs(difference)) + percent + '.';
}
