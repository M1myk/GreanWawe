import { useEffect, useRef, useState } from 'react';
import { SIMULATION_POLL_MS, type SimulationSnapshot } from './BaselineControls';

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
  if (snapshot.status !== 'RUNNING' || frame?.snapshot.id !== snapshot.id
    || frame.distance > snapshot.metrics.distanceM) return snapshot.vehicle.position;
  return positionAlongRoute(snapshot, frame.distance);
}

function positionAlongRoute(snapshot: SimulationSnapshot, distance: number): [number, number] {
  const { routeGeometry, routeCumulativeMeters } = snapshot;
  if (!routeCumulativeMeters || routeCumulativeMeters.length !== routeGeometry.length) return snapshot.vehicle.position;
  let low = 0;
  let high = routeCumulativeMeters.length - 1;
  while (high - low > 1) {
    const middle = Math.floor((low + high) / 2);
    if (routeCumulativeMeters[middle] > distance) high = middle;
    else low = middle;
  }
  const length = routeCumulativeMeters[high] - routeCumulativeMeters[low];
  if (length <= 0) return snapshot.vehicle.position;
  const ratio = Math.min(1, Math.max(0, (distance - routeCumulativeMeters[low]) / length));
  const [lon, lat] = routeGeometry[low];
  const [nextLon, nextLat] = routeGeometry[high];
  return [lon + (nextLon - lon) * ratio, lat + (nextLat - lat) * ratio];
}
