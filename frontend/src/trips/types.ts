export const trafficLabels = { NONE: 'Bez natężenia', NORMAL: 'Zwykły ruch', RUSH_HOUR: 'Godziny szczytu' } as const;
export type TrafficMode = keyof typeof trafficLabels;
export const trafficModes = Object.keys(trafficLabels) as TrafficMode[];

export interface TripConfig {
  scenarioId: string;
  startNodeId: string;
  destinationNodeId: string;
  randomSeed: number;
  vehicle: { maxSpeedMps: number; accelerationMps2: number; brakingMps2: number };
  navigation: { observationRangeM: number; decisionIntervalSeconds: number };
  maxDurationSeconds: number;
  trafficMode?: TrafficMode;
}
export interface TripPreset { id: string; name: string; config: TripConfig; version: number; createdAt: string; updatedAt: string }
export interface TripPage { items: TripPreset[]; total: number; limit: number; offset: number }
export interface Scenario { id: string; name: string; version: string; networkId: string; networkVersion: string }
export interface ScenarioDetails extends Scenario { nodes: { id: string; lat: number; lon: number }[] }
export interface FieldError { field: string; message: string }
export const defaultConfig: TripConfig = {
  scenarioId: 'gdansk-v3', startNodeId: '', destinationNodeId: '', randomSeed: 42,
  vehicle: { maxSpeedMps: 13.89, accelerationMps2: 2, brakingMps2: 3 },
  navigation: { observationRangeM: 1500, decisionIntervalSeconds: 1 }, maxDurationSeconds: 1800, trafficMode: 'NONE',
};
