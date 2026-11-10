import { useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent } from 'react';
import { request } from '../api/client';

interface Node { id: string; lat: number; lon: number }
interface Network { nodes: Node[]; edges: { fromNodeId: string; toNodeId: string; name: string; lengthM: number }[] }
type Endpoint = 'startNodeId' | 'destinationNodeId';
interface Point { x: number; y: number }
interface PinDrag {
  field: Endpoint;
  pointerId: number;
  offset: Point;
  position: Point;
}

interface Props {
  networkId: string;
  fullScreen?: boolean;
  openSaved?: () => void;
  start: string;
  destination: string;
  onSelect: (field: Endpoint, id: string) => void;
}

export function RoadMap({ networkId, start, destination, onSelect, fullScreen, openSaved }: Props) {
  const [network, setNetwork] = useState<Network>();
  const [error, setError] = useState(false);
  const [field, setField] = useState<Endpoint>('startNodeId');

  const [drag, setDrag] = useState<PinDrag>();
  const [hint, setHint] = useState('');
  function activate(next: Endpoint) {
    setHint('');
    setField(next);
  }

  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void request<Network>('/networks/' + encodeURIComponent(networkId), { signal: controller.signal })
      .then(setNetwork)
      .catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [networkId, retry]);

  const geographic = Boolean(fullScreen && networkId !== 'synthetic-v1');
  const drawing = useMemo(() => network ? drawNetwork(network, geographic) : undefined, [network, geographic]);
  const canvas = useRef<SVGSVGElement>(null);
  const [size, setSize] = useState({ width: 600, height: 420 });
  const [zoom, setZoom] = useState(0);

  useEffect(() => {
    const element = canvas.current;
    if (!geographic || !element) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      if (width > 0 && height > 0) setSize({ width, height });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [drawing, geographic]);
  const streetNames = drawing?.streetNames;
  const panel = fullScreen && <section className="route-panel" aria-label="Wybór punktów">
    <header><strong>GreenWave</strong><button type="button" onClick={openSaved}>Zapisane</button></header>
    {(['startNodeId', 'destinationNodeId'] as const).map((key, index) => {
      const selected = index ? destination : start;
      return <EndpointField key={key + ':' + selected} label={index ? 'Dokąd' : 'Skąd'}
        marker={index ? 'B' : 'A'} selected={selected} names={streetNames}
        active={field === key} activate={() => activate(key)}
        clear={() => { onSelect(key, ''); activate(key); }}
        choose={id => { onSelect(key, id); activate(key === 'startNodeId' ? 'destinationNodeId' : key); }} />;
    })}
    <p role="status">{error ? 'Nie udało się wczytać mapy.' : !drawing ? 'Wczytywanie mapy…'
      : hint || (field === 'startNodeId' ? 'Wybierasz A · start' : 'Wybierasz B · cel')}</p>
    {drawing && !error && <p className="map-tip">Kliknij drogę lub przeciągnij pinezkę.</p>}
    {error && <button type="button" onClick={() => { setError(false); setRetry(value => value + 1); }}>Spróbuj ponownie</button>}
  </section>;
  if (!drawing) return fullScreen ? panel : <p role={error ? 'alert' : 'status'}>
    {error ? 'Road map unavailable. Use the node lists below.' : 'Loading road map…'}</p>;
  const { points, paths, majorRoads, bounds } = drawing;
  const radius = Math.hypot(bounds.width, bounds.height) / 2;
  const center = { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 };
  const scale = Math.min(size.width, size.height) / (2.55 * Math.max(radius, 1)) * 1.5 ** zoom;
  const width = size.width / scale;
  const height = size.height / scale;
  const view = { x: center.x + radius * 0.12 - width / 2,
    y: center.y - radius * 0.15 - height / 2, width, height };
  const tiles = geographic ? visibleTiles(view, scale) : [];
  const pinScale = geographic ? 1 / scale : 1;
  return <div className={fullScreen ? "map-surface" : undefined}>
    {!fullScreen && <div className="actions">
      <button type="button" aria-pressed={field === 'startNodeId'} onClick={() => setField('startNodeId')}>Pick start</button>
      <button type="button" aria-pressed={field === 'destinationNodeId'} onClick={() => setField('destinationNodeId')}>Pick destination</button>
    </div>}
    <svg ref={canvas} viewBox={geographic ? `${view.x} ${view.y} ${view.width} ${view.height}` : '0 0 600 420'} width="100%" aria-label="Road network. Use the node lists for keyboard selection."
      className="road-canvas"
      onClick={event => {
        const transform = event.currentTarget.getScreenCTM();
        if (!transform) return;
        const closest = nearestNode(points, transform, { x: event.clientX, y: event.clientY });
        if (closest) {
          onSelect(field, closest);
          activate(fullScreen && field === 'startNodeId' ? 'destinationNodeId' : field);
        } else if (fullScreen) {
          setHint(field === 'startNodeId' ? 'A · Kliknij bliżej drogi.' : 'B · Kliknij bliżej drogi.');
        }
      }}>
      <path d={paths} stroke="#bbc8bd" fill="none" strokeWidth="2.8" />
      <path d={paths} stroke="#fff" fill="none" strokeWidth="1.6" />
      {[...majorRoads.values()].map(edge => {
        const a = points.get(edge.fromNodeId)!;
        const b = points.get(edge.toNodeId)!;
        return <text key={edge.name} x={(a.x + b.x) / 2} y={(a.y + b.y) / 2}
          fill="#173c30" stroke="#e8efe9" strokeWidth="3" paintOrder="stroke"
          textAnchor="middle" fontSize="10">{edge.name}</text>;
      })}
      {tiles.map(tile => <image key={tile.url} href={tile.url} x={tile.x} y={tile.y}
        width={tile.size} height={tile.size} pointerEvents="none" />)}
      {geographic && <g pointerEvents="none">
        <circle cx={center.x} cy={center.y} r={radius} fill="#1670df" fillOpacity=".06"
          stroke="#1462bc" strokeWidth="2" strokeDasharray="7 5" vectorEffect="non-scaling-stroke" />
        <rect x={bounds.x} y={bounds.y} width={bounds.width} height={bounds.height}
          fill="none" stroke="#1462bc" strokeWidth="1" vectorEffect="non-scaling-stroke" />
      </g>}
      {[start, destination].map((id, index) => {
        const endpoint = index ? 'destinationNodeId' : 'startNodeId';
        const dragging = drag?.field === endpoint ? drag : undefined;
        const point = dragging?.position ?? points.get(id);
        if (!point) return null;

        function positionAt(event: PointerEvent<SVGGElement>, offset: Point): Point | undefined {
          const transform = event.currentTarget.ownerSVGElement?.getScreenCTM();
          if (!transform) return undefined;
          const position = new DOMPoint(event.clientX, event.clientY).matrixTransform(transform.inverse());
          return { x: position.x + offset.x, y: position.y + offset.y };
        }

        return <g key={endpoint} transform={`translate(${point.x} ${point.y}) scale(${pinScale})`}
          className={fullScreen ? 'draggable-pin' : undefined}
          pointerEvents={fullScreen ? undefined : 'none'}
          onClick={event => { if (fullScreen) event.stopPropagation(); }}
          onPointerDown={event => {
            if (!fullScreen || drag || !event.isPrimary || event.button !== 0) return;
            const pointer = positionAt(event, { x: 0, y: 0 });
            if (!pointer) return;
            event.currentTarget.setPointerCapture(event.pointerId);
            activate(endpoint);
            setDrag({ field: endpoint, pointerId: event.pointerId, position: point,
              offset: { x: point.x - pointer.x, y: point.y - pointer.y } });
          }}
          onPointerMove={event => {
            if (!dragging || dragging.pointerId !== event.pointerId) return;
            const position = positionAt(event, dragging.offset);
            if (position) setDrag({ ...dragging, position });
          }}
          onPointerUp={event => {
            if (!dragging || dragging.pointerId !== event.pointerId) return;
            const transform = event.currentTarget.ownerSVGElement?.getScreenCTM();
            const position = positionAt(event, dragging.offset);
            const closest = transform && position && nearestNode(points, transform,
              new DOMPoint(position.x, position.y).matrixTransform(transform));
            if (closest) onSelect(endpoint, closest);
            else setHint(`${index ? 'B' : 'A'} · Upuść pinezkę bliżej drogi.`);
            setDrag(undefined);
          }}
          onLostPointerCapture={event => {
            setDrag(current => current?.pointerId === event.pointerId ? undefined : current);
          }}>
          <title>{index ? 'Przeciągnij cel B' : 'Przeciągnij start A'}</title>
          <circle cy="-17" r="16" fill="transparent" />
          <path d="M0 0C-4 -7 -11 -12 -11 -20a11 11 0 1 1 22 0C11 -12 4 -7 0 0Z"
            fill={index ? '#cf392c' : '#1670df'} stroke="white" strokeWidth="1.5" />
          <text y="-16" textAnchor="middle" fill="white" fontSize="12" fontWeight="700">{index ? 'B' : 'A'}</text>
        </g>;
      })}
    </svg>
    {panel}
    {geographic && <div className="map-controls" aria-label="Sterowanie mapą">
      <span>Obszar działania GreenWave</span>
      <button type="button" aria-label="Powiększ mapę" disabled={zoom >= 4} onClick={() => setZoom(value => value + 1)}>+</button>
      <button type="button" aria-label="Pomniejsz mapę" disabled={zoom <= -2} onClick={() => setZoom(value => value - 1)}>−</button>
    </div>}
    {networkId !== 'synthetic-v1' && <p className="map-credit"><a href="https://www.openstreetmap.org/copyright">© OpenStreetMap contributors</a></p>}
  </div>;
}

function nearestNode(points: Map<string, Point>, transform: DOMMatrix, target: Point) {
  let closest = '';
  let distance = 20;
  for (const [id, point] of points) {
    const screen = new DOMPoint(point.x, point.y).matrixTransform(transform);
    const candidate = Math.hypot(screen.x - target.x, screen.y - target.y);
    if (candidate < distance) {
      closest = id;
      distance = candidate;
    }
  }
  return closest;
}

const WORLD_SIZE = 256 * 2 ** 14;
const tileTemplate = import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

function project(node: Node): Point {
  const sine = Math.sin(node.lat * Math.PI / 180);
  return {
    x: WORLD_SIZE * (node.lon + 180) / 360,
    y: WORLD_SIZE * (0.5 - Math.log((1 + sine) / (1 - sine)) / (4 * Math.PI)),
  };
}

function visibleTiles(view: Point & { width: number; height: number }, scale: number) {
  const level = Math.max(11, Math.min(18, Math.round(14 + Math.log2(scale))));
  const size = WORLD_SIZE / 2 ** level;
  const tiles = [];
  for (let x = Math.floor(view.x / size); x < Math.ceil((view.x + view.width) / size); x++) {
    for (let y = Math.floor(view.y / size); y < Math.ceil((view.y + view.height) / size); y++) {
      tiles.push({ x: x * size, y: y * size, size,
        url: tileTemplate.replace('{z}', String(level)).replace('{x}', String(x)).replace('{y}', String(y)) });
    }
  }
  return tiles;
}

function drawNetwork(network: Network, geographic: boolean) {
  const latitudes = network.nodes.map(node => node.lat);
  const longitudes = network.nodes.map(node => node.lon);
  const west = Math.min(...longitudes);
  const north = Math.max(...latitudes);
  const longitudeScale = Math.cos(north * Math.PI / 180);
  const scale = Math.min(580 / Math.max(0.00001, (Math.max(...longitudes) - west) * longitudeScale),
    400 / Math.max(0.00001, north - Math.min(...latitudes)));
  const points = new Map(network.nodes.map(node => [node.id, geographic ? project(node) : {
    x: 10 + (node.lon - west) * longitudeScale * scale, y: 10 + (north - node.lat) * scale,
  }]));
  const paths = network.edges.map(edge => {
    const a = points.get(edge.fromNodeId);
    const b = points.get(edge.toNodeId);
    return a && b ? `M${a.x},${a.y}L${b.x},${b.y}` : '';
  }).join(' ');
  const streetNames = new Map<string, string>();
  for (const edge of network.edges) {
    if (!edge.name) continue;
    if (!streetNames.has(edge.fromNodeId)) streetNames.set(edge.fromNodeId, edge.name);
    if (!streetNames.has(edge.toNodeId)) streetNames.set(edge.toNodeId, edge.name);
  }
  const majorRoads = new Map<string, Network['edges'][number]>();
  for (const edge of network.edges) {
    if (!/Grunwaldzka|Kołobrzeska|Chłopska|Jana Pawła II|Rzeczypospolitej|Czarny Dwór|Hallera|Pomorska/.test(edge.name)) continue;
    if (edge.lengthM > (majorRoads.get(edge.name)?.lengthM ?? 0)) majorRoads.set(edge.name, edge);
  }
  const xs = [...points.values()].map(point => point.x);
  const ys = [...points.values()].map(point => point.y);
  const x = Math.min(...xs);
  const y = Math.min(...ys);
  const bounds = { x, y, width: Math.max(...xs) - x, height: Math.max(...ys) - y };
  return { points, paths, majorRoads, streetNames, bounds };
}


interface EndpointProps {
  label: string;
  marker: string;
  selected: string;
  names?: Map<string, string>;
  active: boolean;
  activate: () => void;
  clear: () => void;
  choose: (id: string) => void;
}

function EndpointField({ label, marker, selected, names, active, activate, choose, clear }: EndpointProps) {
  const [query, setQuery] = useState<string>();
  const term = query?.trim().toLocaleLowerCase('pl') ?? '';
  const matches = new Map<string, string>();
  if (active && term.length >= 2 && names) {
    for (const [id, name] of names) {
      if (name.toLocaleLowerCase('pl').includes(term) && !matches.has(name)) matches.set(name, id);
      if (matches.size === 6) break;
    }
  }

  return <div className={`endpoint-field endpoint-${marker}${active ? ' is-active' : ''}`} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setQuery(undefined);
  }}>
    <div className="endpoint-row"><label><span className={`endpoint-marker marker-${marker}`}>{marker}</span>
      <span>{label}</span>
      <input type="search" placeholder="Wpisz ulicę lub kliknij mapę" value={query ?? (selected ? names?.get(selected) ?? `Punkt ${selected}` : '')}
        onFocus={event => { activate(); event.currentTarget.select(); }}
        onChange={event => setQuery(event.currentTarget.value)} />
    </label>
      {selected && <button className="clear-endpoint" type="button" aria-label={`Usuń punkt ${marker}`}
        onClick={() => { setQuery(undefined); clear(); }}>×</button>}
    </div>
    {active && term.length >= 2 && names && <ul className="street-matches">
      {matches.size === 0 && <li>Brak ulic w tym wycinku.</li>}
      {[...matches].map(([name, id]) => <li key={name}>
        <button type="button" onClick={() => { choose(id); setQuery(undefined); }}>{name}</button>
      </li>)}
    </ul>}
  </div>;
}
