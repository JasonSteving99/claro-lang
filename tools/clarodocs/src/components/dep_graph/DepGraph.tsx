import { Mermaid } from '../mermaid/Mermaid';

export function DepGraph({ selectedModule, setSelectedModule, deps, dependents }) {
  let mermaidGraph = 'graph LR;\n';
  const nodeOnClickCallbacks = {};
  if (Object.entries(deps).length > 0) {
    Object.entries(deps)
      .toSorted((d1, d2) => d1[0].localeCompare(d2[0]))
      .forEach(
        ([dep, { path } ]) => {
          mermaidGraph += `${selectedModule}{{${selectedModule}}}--${dep}-->${path};`;
          nodeOnClickCallbacks[path] = () => setSelectedModule(path);
        }
      );
  } else {
    mermaidGraph += `${selectedModule}-- "--No Deps--" --oNOTHING[" "];`;
    mermaidGraph += `style NOTHING display:none;`;
  }
  dependents
    .toSorted((d1, d2) => d1.dependentName.localeCompare(d2.dependentName))
    .forEach(
      ({dependentName, depName}) => {
        mermaidGraph += `${dependentName}-.->|${depName}|${selectedModule}{{${selectedModule}}};`;
        nodeOnClickCallbacks[dependentName] = () => setSelectedModule(dependentName);
      }
    );
  mermaidGraph += `style ${selectedModule} fill:#fff;`;
  return (
    <Mermaid
      chart={mermaidGraph}
      selectedModule={selectedModule}
      nodeOnClickCallbacks={nodeOnClickCallbacks}
    />
  )
}