import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { TripPresets } from './trips/TripPresets';

const apiBase = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1').replace(/\/$/, '');
type Connection = 'checking' | 'connected' | 'unavailable';

function App() {
  const [connection, setConnection] = useState<Connection>('checking');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 5000);
    let active = true;
    setConnection('checking');
    async function checkConnection() {
      try {
        const response = await fetch(`${apiBase}/health`, { signal: controller.signal });
        if (!response.ok) throw new Error('Health request failed');
        const health: unknown = await response.json();
        if (typeof health !== 'object' || health === null || !('status' in health)
          || health.status !== 'UP' || !('application' in health) || health.application !== 'greenwave') {
          throw new Error('Unexpected health response');
        }
        if (active) setConnection('connected');
      } catch {
        if (active) setConnection('unavailable');
      } finally {
        window.clearTimeout(timeout);
      }
    }
    void checkConnection();
    return () => { active = false; window.clearTimeout(timeout); controller.abort(); };
  }, [attempt]);

  return (
    <main>
      <p className="eyebrow">Urban journey simulation</p>
      <h1>GreenWave</h1>
      <p>Compare baseline routing with signal-aware navigation.</p>
      <section aria-labelledby="connection-title">
        <h2 id="connection-title">Development environment</h2>
        <p role="status" aria-live="polite">
          {connection === 'checking' && 'Checking API connection…'}
          {connection === 'connected' && 'Connected to the GreenWave API.'}
          {connection === 'unavailable' && 'API unavailable. Check that the backend is running and the API address and CORS origin are correct.'}
        </p>
        <button disabled={connection === 'checking'} onClick={() => setAttempt(value => value + 1)}>Retry connection</button>
      </section>
      <TripPresets />
    </main>
  );
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
