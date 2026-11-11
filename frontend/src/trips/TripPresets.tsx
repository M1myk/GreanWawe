import { RoadMap } from './RoadMap';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, request } from '../api/client';
import { NumberField } from './NumberField';
import { defaultConfig, trafficLabels, trafficModes, type TrafficMode } from './types';
import type { Scenario, ScenarioDetails, TripConfig, TripPage, TripPreset } from './types';

const pageSize = 5;
export function TripPresets({ onLaunch }: { onLaunch: (config: TripConfig, networkId: string) => void }) {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [details, setDetails] = useState<ScenarioDetails | null>(null);
  const [config, setConfig] = useState<TripConfig>(structuredClone(defaultConfig));
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<TripPreset | null>(null);
  const [page, setPage] = useState<TripPage | null>(null);
  const [offset, setOffset] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [message, setMessage] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void request<{ items: Scenario[] }>('/scenarios', { signal: controller.signal })
      .then(data => setScenarios(data.items)).catch(handle);
    return () => controller.abort();
  }, [refresh]);

  useEffect(() => {
    const controller = new AbortController();
    setDetails(null);
    void request<ScenarioDetails>(`/scenarios/${encodeURIComponent(config.scenarioId)}`, { signal: controller.signal })
      .then(setDetails).catch(handle);
    return () => controller.abort();
  }, [config.scenarioId, refresh]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void request<TripPage>(`/trip-presets?limit=${pageSize}&offset=${offset}`, { signal: controller.signal })
      .then(data => { setPage(data); setLoading(false); })
      .catch(reason => { if (!controller.signal.aborted) { handle(reason); setLoading(false); } });
    return () => controller.abort();
  }, [offset, refresh]);

  function handle(reason: unknown) {
    if (reason instanceof DOMException && reason.name === 'AbortError') return;
    setError(reason instanceof ApiError ? reason : new ApiError('Unexpected response. Please retry.', 0));
  }
  function newTrip() {
    setEditing(null); setName(''); setConfig(structuredClone(defaultConfig)); setError(null); setMessage('');
  }
  async function edit(id: string) {
    setBusy(true); setError(null); setMessage('');
    try {
      const preset = await request<TripPreset>(`/trip-presets/${encodeURIComponent(id)}`);
      setEditing(preset); setName(preset.name); setConfig(preset.config);
    } catch (reason) { handle(reason); } finally { setBusy(false); }
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(null); setMessage('');
    try {
      const saved = await request<TripPreset>(editing ? `/trip-presets/${encodeURIComponent(editing.id)}` : '/trip-presets', {
        method: editing ? 'PUT' : 'POST', body: JSON.stringify({ name, config, ...(editing ? { version: editing.version } : {}) }),
      });
      setEditing(saved); setName(saved.name); setConfig(saved.config); setMessage('Trip saved.');
      setOffset(0); setRefresh(value => value + 1);
    } catch (reason) { handle(reason); } finally { setBusy(false); }
  }
  async function remove(id: string) {
    setBusy(true); setError(null); setMessage('');
    try {
      await request<void>(`/trip-presets/${encodeURIComponent(id)}`, { method: 'DELETE' });
      if (editing?.id === id) newTrip();
      setConfirmDelete(null); setMessage('Trip deleted.');
      if (page?.items.length === 1 && offset > 0) setOffset(Math.max(0, offset - pageSize));
      setRefresh(value => value + 1);
    } catch (reason) { handle(reason); } finally { setBusy(false); }
  }
  const vehicle = (key: keyof TripConfig['vehicle'], value: number) => setConfig(current => ({ ...current, vehicle: { ...current.vehicle, [key]: value } }));
  const navigation = (key: keyof TripConfig['navigation'], value: number) => setConfig(current => ({ ...current, navigation: { ...current.navigation, [key]: value } }));

  const fieldError = (path: string) => error?.fields.find(field => field.field === path)?.message;

  return <div className="workspace">
    <section aria-labelledby="form-title">
      <h2 id="form-title">{editing ? 'Edit saved trip' : 'Create saved trip'}</h2>
      <p className="note">Choose a start and destination on the map or from the node lists.</p>
      {error && <div role="alert" className="error"><p>{error.message}</p>
        <ul>{error.fields.map(field => <li key={field.field}>{field.field}: {field.message}</li>)}</ul>
        {error.status === 409 && editing && <button disabled={busy} onClick={() => void edit(editing.id)}>Reload saved version (discard edits)</button>}
        <button disabled={busy} onClick={() => { setError(null); setRefresh(value => value + 1); }}>Retry loading</button>
      </div>}
      <p role="status">{message}</p>
      <form onSubmit={event => void save(event)}>
        <fieldset disabled={busy}>
          <label>Trip name<input required maxLength={80} aria-invalid={!!fieldError('name')} value={name} onChange={event => setName(event.target.value)} />{fieldError('name') && <small className="field-error">{fieldError('name')}</small>}</label>
          <label>Scenario<select value={config.scenarioId} onChange={event => setConfig(current => ({ ...current, scenarioId: event.target.value, startNodeId: '', destinationNodeId: '' }))}>
            {scenarios.map(scenario => <option key={scenario.id} value={scenario.id}>{scenario.name}</option>)}
          </select></label>
          {details?.id === config.scenarioId && <RoadMap key={details.networkId} networkId={details.networkId}
            start={config.startNodeId} destination={config.destinationNodeId}
            onSelect={(field, id) => setConfig(current => ({ ...current, [field]: id }))} />}
          <div className="fields">
            <label>Start node<select required aria-invalid={!!fieldError('config.startNodeId')} value={config.startNodeId} onChange={event => setConfig(current => ({ ...current, startNodeId: event.target.value }))}>
              <option value="">Select start</option>{details?.nodes.map(node => <option key={node.id}>{node.id}</option>)}
            </select>{fieldError('config.startNodeId') && <small className="field-error">{fieldError('config.startNodeId')}</small>}</label>
            <label>Destination node<select required aria-invalid={!!fieldError('config.destinationNodeId')} value={config.destinationNodeId} onChange={event => setConfig(current => ({ ...current, destinationNodeId: event.target.value }))}>
              <option value="">Select destination</option>{details?.nodes.map(node => <option key={node.id}>{node.id}</option>)}
            </select>{fieldError('config.destinationNodeId') && <small className="field-error">{fieldError('config.destinationNodeId')}</small>}</label>
            <label>Ruch samochodowy<select value={config.trafficMode ?? 'NONE'}
              onChange={event => setConfig(current => ({ ...current, trafficMode: event.target.value as TrafficMode }))}>
              {trafficModes.map(mode => <option key={mode} value={mode}>{trafficLabels[mode]}</option>)}
            </select></label>
            <NumberField error={fieldError('config.vehicle.maxSpeedMps')} label="Maximum speed (km/h)" min={0.001} max={144} step={0.001} value={Number((config.vehicle.maxSpeedMps * 3.6).toFixed(3))} onChange={value => vehicle('maxSpeedMps', value / 3.6)} />
            <NumberField error={fieldError('config.vehicle.accelerationMps2')} label="Acceleration (m/s²)" min={0.1} max={10} step={0.1} value={config.vehicle.accelerationMps2} onChange={value => vehicle('accelerationMps2', value)} />
            <NumberField error={fieldError('config.vehicle.brakingMps2')} label="Braking (m/s²)" min={0.1} max={10} step={0.1} value={config.vehicle.brakingMps2} onChange={value => vehicle('brakingMps2', value)} />
            <NumberField error={fieldError('config.randomSeed')} label="Random seed" min={0} max={2147483647} value={config.randomSeed} onChange={value => setConfig(current => ({ ...current, randomSeed: value }))} />
            <NumberField error={fieldError('config.navigation.observationRangeM')} label="Observation range (m)" min={50} max={2000} value={config.navigation.observationRangeM} onChange={value => navigation('observationRangeM', value)} />
            <NumberField error={fieldError('config.navigation.decisionIntervalSeconds')} label="Decision interval (s)" min={0.1} max={10} step={0.1} value={config.navigation.decisionIntervalSeconds} onChange={value => navigation('decisionIntervalSeconds', value)} />
            <NumberField error={fieldError('config.maxDurationSeconds')} label="Simulation time limit (s)" min={10} max={7200} step={0.1} value={config.maxDurationSeconds} onChange={value => setConfig(current => ({ ...current, maxDurationSeconds: value }))} />
          </div>
          <div className="actions"><button type="submit" disabled={!details}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Save trip'}</button>
            <button type="button" className="secondary" onClick={newTrip}>New trip</button></div>
        </fieldset>
      </form>
    </section>
    <section aria-labelledby="list-title">
      <h2 id="list-title">Saved trips</h2>
      {loading && <p role="status">Loading saved trips…</p>}
      {!loading && page?.total === 0 && <p>No saved trips yet.</p>}
      <ul className="trip-list">{page?.items.map(trip => <li key={trip.id}>
        <strong>{trip.name}</strong><p>{trip.config.startNodeId} → {trip.config.destinationNodeId} · version {trip.version}</p>
        <p className="note">Updated {new Date(trip.updatedAt).toLocaleString()}</p>
        <div className="actions"><button disabled={busy || loading} onClick={() => void edit(trip.id)}>Edit {trip.name}</button>
          <button className="secondary" disabled={busy || loading || !scenarios.some(item => item.id === trip.config.scenarioId)}
            onClick={() => {
              const scenario = scenarios.find(item => item.id === trip.config.scenarioId);
              if (scenario) onLaunch(trip.config, scenario.networkId);
            }}>Porównaj {trip.name}</button>
          <button className="secondary" disabled={busy || loading} onClick={() => setConfirmDelete(trip.id)}>Delete {trip.name}</button></div>
        {confirmDelete === trip.id && <div role="group" aria-label="Confirm deletion"><p>Delete this saved trip?</p>
          <button disabled={busy} onClick={() => void remove(trip.id)}>Confirm delete</button>{' '}
          <button className="secondary" disabled={busy} onClick={() => setConfirmDelete(null)}>Cancel</button>
        </div>}
      </li>)}</ul>
      <div className="actions"><button className="secondary" disabled={busy || loading || offset === 0} onClick={() => setOffset(value => Math.max(0, value - pageSize))}>Previous</button>
        <span>{page ? `${page.total} saved · page ${Math.floor(offset / pageSize) + 1}` : ''}</span>
        <button className="secondary" disabled={busy || loading || !page || offset + pageSize >= page.total} onClick={() => setOffset(value => value + pageSize)}>Next</button></div>
    </section>
  </div>;
}
