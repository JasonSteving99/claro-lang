import React, { useEffect, useRef } from 'react';
import ReactDOM from 'react-dom/client';
import mermaid from 'mermaid';

mermaid.initialize({
  startOnLoad: false,
  theme: 'forest'
});

export function Mermaid({ chart, selectedModule, nodeOnClickCallbacks = {} }) {
  const ref = useRef(null);
  useEffect(
    () => {
      async function renderGraph() {
        const { svg, bindFunctions } = await mermaid.render('graphDiv', chart);
        ref.current.innerHTML = svg;
        if (bindFunctions) {
          bindFunctions(ref.current);

          Object.entries(nodeOnClickCallbacks).forEach(
            ([ nodeName, onClick ]) => {
              const nodeEl = document.querySelector(`g[data-id="${nodeName}"]`);
              if (nodeEl) {
              nodeEl.addEventListener('click', onClick);
              nodeEl.classList.add('clickable');
              }
            }
          );
        }
      }
      renderGraph();
    },
    [selectedModule]
  );

  return (
    <div ref={ref}/>
  )
}