import { useId } from 'react';

interface Props { label: string; value: number; onChange: (value: number) => void; min: number; max: number; step?: number; error?: string }
export function NumberField({ label, value, onChange, min, max, step = 1, error }: Props) {
  const errorId = useId();
  return <label>{label}<input type="number" required min={min} max={max} step={step}
    aria-invalid={!!error} aria-describedby={error ? errorId : undefined}
    value={Number.isNaN(value) ? '' : value} onChange={event => onChange(event.currentTarget.valueAsNumber)} />{error && <small id={errorId} className="field-error">{error}</small>}</label>;
}
