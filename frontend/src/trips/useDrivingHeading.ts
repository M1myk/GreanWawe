import { useEffect, useRef, useState } from 'react';

export function useDrivingHeading(target: number, driving: boolean) {
  const [heading, setHeading] = useState(target);
  const displayed = useRef(target);

  useEffect(() => {
    if (!driving || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      displayed.current = target;
      setHeading(target);
      return;
    }

    let frame: number;
    let previous = performance.now();
    function animate(now: number) {
      const difference = target - displayed.current;
      const arc = Math.atan2(Math.sin(difference), Math.cos(difference));
      const done = Math.abs(arc) < 0.001;
      displayed.current += done ? arc : arc * (1 - Math.exp(-(now - previous) / 105));
      previous = now;
      setHeading(displayed.current);
      if (!done) frame = requestAnimationFrame(animate);
    }
    frame = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frame);
  }, [target, driving]);

  return driving ? heading : 0;
}
