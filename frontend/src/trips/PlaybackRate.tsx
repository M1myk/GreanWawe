import { useEffect, useId, useRef } from 'react';

const rates = [0.5, 1, 2, 5, 10];
const viewportGap = 8;
const triggerGap = 5;

export function PlaybackRate({ rate, disabled, onChange }: {
  rate: number;
  disabled: boolean;
  onChange: (rate: number) => void;
}) {
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const button = trigger.current!;
    const popup = menu.current!;
    let frame = 0;

    function position() {
      if (!popup.matches(':popover-open')) return;

      const anchor = button.getBoundingClientRect();
      const panel = button.closest('.route-panel')?.getBoundingClientRect();
      if (panel && (anchor.bottom <= panel.top || anchor.top >= panel.bottom)) {
        popup.hidePopover();
        return;
      }

      const above = anchor.top - triggerGap - viewportGap;
      const below = window.innerHeight - anchor.bottom - triggerGap - viewportGap;
      const popupStyle = getComputedStyle(popup);
      const height = popup.scrollHeight + parseFloat(popupStyle.borderTopWidth) + parseFloat(popupStyle.borderBottomWidth);
      const openAbove = below < height && above > below;
      popup.style.maxHeight = `${Math.max(0, openAbove ? above : below)}px`;
      const left = Math.min(anchor.left, window.innerWidth - popup.offsetWidth - viewportGap);
      popup.style.left = `${Math.max(viewportGap, left)}px`;
      popup.style.top = `${openAbove ? anchor.top - triggerGap - popup.offsetHeight : anchor.bottom + triggerGap}px`;
      frame = requestAnimationFrame(position);
    }

    function onToggle() {
      cancelAnimationFrame(frame);
      if (!popup.matches(':popover-open')) return;
      position();
      popup.querySelector<HTMLButtonElement>('[aria-pressed="true"]')?.focus({ preventScroll: true });
    }

    popup.addEventListener('toggle', onToggle);
    return () => {
      cancelAnimationFrame(frame);
      popup.removeEventListener('toggle', onToggle);
    };
  }, []);

  function dismiss() {
    menu.current?.hidePopover();
    trigger.current?.focus({ preventScroll: true });
  }

  return <div className="playback-rate" onKeyDown={event => {
    if (event.key === 'Escape' && menu.current?.matches(':popover-open')) {
      event.preventDefault();
      event.stopPropagation();
      dismiss();
    }
  }}>
    <span>Tempo</span>
    <button ref={trigger} type="button" className="playback-rate-trigger" popoverTarget={id}
      aria-label={`Tempo ${rate}×`} disabled={disabled}>
      {rate}× <span aria-hidden="true">⌄</span>
    </button>
    <div ref={menu} id={id} popover="auto" className="playback-rate-options" role="group" aria-label="Tempo odtwarzania">
      {rates.map(option => <button key={option} type="button" aria-pressed={rate === option} onClick={() => {
        dismiss();
        if (option !== rate) onChange(option);
      }}>{option}×</button>)}
    </div>
  </div>;
}
