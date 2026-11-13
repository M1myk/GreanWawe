import { useEffect, useRef, useState } from 'react';
import { SIMULATION_POLL_MS, type SimulationSnapshot } from './ComparisonControls';

type Sample = { snapshot: SimulationSnapshot; distance: number };

export function useVehiclePosition(snapshot?: SimulationSnapshot) {
  const [frame, setFrame] = useState<Sample>();
  const previous = useRef<SimulationSnapshot | undefined>(undefined);
  const displayed = useRef<number | undefined>(undefined);

  useEffect(() => {
    const prior = previous.current;
    previous.current = snapshot;
    if (!snapshot) { displayed.current = undefined; return; }

    const target = snapshot.metrics.distanceM;
    const from = displayed.current ?? target;
    if (!prior || prior.id !== snapshot.id || snapshot.status !== 'RUNNING'
      || prior.status !== 'RUNNING' || target <= from
      || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      displayed.current = target;
      setFrame({ snapshot, distance: target });
      return;
    }

    const start = performance.now();
    const sample = snapshot;
    let handle: number;
    function animate(now: number) {
      const fraction = Math.min(1, (now - start) / SIMULATION_POLL_MS);
      const distance = from + (target - from) * fraction;
      displayed.current = distance;
      setFrame({ snapshot: sample, distance });
      if (fraction < 1) handle = requestAnimationFrame(animate);
    }
    handle = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(handle);
  }, [snapshot]);

  if (!snapshot) return undefined;
  const confirmed = snapshot.metrics.distanceM;
  const interpolate = snapshot.status === 'RUNNING' && frame?.snapshot.id === snapshot.id && frame.distance <= confirmed;
  const distance = interpolate ? frame.distance : confirmed;
  return {
    position: interpolate ? positionAlongRoute(snapshot, distance) : snapshot.vehicle.position,
    segment: routeSegment(snapshot, distance),
  };
}

export function routeSegment(snapshot: SimulationSnapshot, distance: number) {
  const { routeGeometry, routeCumulativeMeters } = snapshot;
  if (routeGeometry.length < 2 || routeCumulativeMeters?.length !== routeGeometry.length) return undefined;
  let low = 0;
  let high = routeCumulativeMeters.length - 1;
  while (high - low > 1) {
    const middle = Math.floor((low + high) / 2);
    if (routeCumulativeMeters[middle] > distance) high = middle;
    else low = middle;
  }
  return { from: routeGeometry[low], to: routeGeometry[high],
    start: routeCumulativeMeters[low], end: routeCumulativeMeters[high] };
}

function positionAlongRoute(snapshot: SimulationSnapshot, distance: number): [number, number] {
  const segment = routeSegment(snapshot, distance);
  if (!segment || segment.end <= segment.start) return snapshot.vehicle.position;
  const ratio = Math.min(1, Math.max(0, (distance - segment.start) / (segment.end - segment.start)));
  const [lon, lat] = segment.from;
  const [nextLon, nextLat] = segment.to;
  return [lon + (nextLon - lon) * ratio, lat + (nextLat - lat) * ratio];
}
