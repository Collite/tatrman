import { useEffect, useRef } from 'react';
import type { Core } from 'cytoscape';
import { renderCanvas, type RenderInput } from '../cy/adapter.js';

/** Mounts a Cytoscape instance for the current canvas and refreshes it when the input changes. */
export function Canvas({ input }: { input: RenderInput }) {
  const ref = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);

  useEffect(() => {
    if (!ref.current) return;
    cyRef.current?.destroy();
    cyRef.current = renderCanvas(input, ref.current);
    const cy = cyRef.current;
    return () => cy.destroy();
  }, [input]);

  return <div ref={ref} data-testid="ttrp-canvas" style={{ width: '100%', height: '100%' }} />;
}
