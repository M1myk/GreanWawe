import type { RouteOption } from './ComparisonControls';

export function alternativeForecast(options: RouteOption[] = [], highlightedId?: string) {
  const selected = options.find(option => option.selected);
  const alternatives = options.filter(option => !option.selected);
  const highlighted = alternatives.find(option => option.id === highlightedId);
  const best = alternatives.reduce<RouteOption | undefined>((fastest, option) =>
    !fastest || option.remainingSeconds < fastest.remainingSeconds ? option : fastest, undefined);
  return { selected, alternative: highlighted ?? best };
}

export function timeDifference(selected: RouteOption | undefined, alternative: RouteOption) {
  if (!selected || alternative.selected) return undefined;
  const difference = selected.remainingSeconds - alternative.remainingSeconds;
  const rounded = Math.round(Math.abs(difference));
  return rounded === 0 ? 0 : Math.sign(difference) * rounded;
}

export function timeDifferenceLabel(seconds: number) {
  if (seconds === 0) return 'Podobny czas';
  const absolute = Math.abs(seconds);
  const minutes = Math.floor(absolute / 60);
  const remainder = absolute % 60;
  const duration = minutes > 0 ? `${minutes} min${remainder ? ` ${remainder} s` : ''}` : `${remainder} s`;
  return `${duration} ${seconds > 0 ? 'szybciej' : 'wolniej'}`;
}
