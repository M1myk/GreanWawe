import assert from 'node:assert/strict';
import { test } from 'node:test';
import { alternativeForecast, timeDifference, timeDifferenceLabel } from '../src/trips/routeForecast.ts';

const selected = { id: 'current', selected: true, remainingSeconds: 100 };
const faster = { id: 'clear', selected: false, remainingSeconds: 33 };
const slower = { id: 'queue', selected: false, remainingSeconds: 115 };

test('the fastest alternative summarizes its estimated gain over the selected route', () => {
  const forecast = alternativeForecast([selected, slower, faster]);
  assert.equal(forecast.alternative, faster);
  assert.equal(timeDifferenceLabel(timeDifference(forecast.selected, forecast.alternative)), '1 min 7 s szybciej');
});

test('the highlighted alternative can take longer than the selected route', () => {
  const forecast = alternativeForecast([selected, slower, faster], slower.id);
  assert.equal(forecast.alternative, slower);
  assert.equal(timeDifferenceLabel(timeDifference(forecast.selected, forecast.alternative)), '15 s wolniej');
});

test('subsecond differences round to a tie and missing routes have no forecast', () => {
  assert.equal(timeDifferenceLabel(timeDifference(selected, { ...slower, remainingSeconds: 100.4 })), 'Podobny czas');
  assert.equal(timeDifference(undefined, faster), undefined);
  assert.equal(timeDifference(selected, selected), undefined);
  assert.equal(alternativeForecast([selected], 'old-id').alternative, undefined);
});
