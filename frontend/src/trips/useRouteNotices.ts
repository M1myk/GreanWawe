import { useEffect, useState } from 'react';
import type { SimulationSnapshot } from './ComparisonControls';

type Notice = NonNullable<SimulationSnapshot['routeNotices']>[number];
type Entry = { notice: Notice; expires: number };

export function useRouteNotices(run?: SimulationSnapshot) {
  const [state, setState] = useState<{ runId?: string; entries: Map<string, Entry> }>();
  const runId = run?.status === 'READY' ? undefined : run?.id;
  const notices = run?.routeNotices;

  useEffect(() => {
    const now = Date.now();
    setState(previous => {
      const entries = previous?.runId === runId ? new Map(previous?.entries) : new Map<string, Entry>();
      for (const notice of notices ?? []) {
        const key = `${notice.id}:${notice.atSeconds ?? notice.reason}`;
        if (!entries.has(key)) entries.set(key, { notice, expires: now + 8000 });
      }
      for (const [key, entry] of entries) {
        if (entry.expires <= now && !(notices ?? []).some(notice =>
          key === `${notice.id}:${notice.atSeconds ?? notice.reason}`)) entries.delete(key);
      }
      return { runId, entries };
    });
  }, [runId, notices]);

  const [, refresh] = useState(0);
  useEffect(() => {
    const now = Date.now();
    const next = Math.min(...Array.from(state?.entries.values() ?? []).map(entry => entry.expires).filter(time => time > now));
    if (!Number.isFinite(next)) return;
    const timer = window.setTimeout(() => refresh(current => current + 1), next - now);
    return () => window.clearTimeout(timer);
  });

  return runId && state?.runId === runId ? Array.from(state.entries.entries())
    .filter(([, entry]) => entry.expires > Date.now()).map(([key, entry]) => ({ key, ...entry.notice })) : [];
}
