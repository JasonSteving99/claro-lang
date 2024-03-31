import { Mermaid } from '../mermaid/Mermaid';

export function WholeProgramDepGraph({ rootName, rootDeps, modules, selectedModule, setSelectedModule, setShowDepGraph }) {
  let mermaidGraph = 'graph LR;\n';

  for (let rootDep of Object.keys(rootDeps).toSorted()) {
    const depName = rootDeps[rootDep].slice(rootDeps[rootDep].indexOf(':') + 1);
    mermaidGraph += `rootNode{{"${rootName}"}}--${rootDep}-->${rootDeps[rootDep]}[${depName}];\n`;
  }

  const nodeOnClickCallbacks = {};
  for (let module of Object.entries(modules)) {
    const moduleName = module[0].slice(module[0].indexOf(':') + 1);
    const deps = module[1].deps;
    if (Object.entries(deps).length > 0) {
      Object.entries(deps)
        .toSorted((d1, d2) => d1[0].localeCompare(d2[0]))
        .forEach(
          ([dep, { path } ]) => {
            const depName = path.slice(path.indexOf(':') + 1);
            mermaidGraph += `${module[0]}[${moduleName}]--${dep}-->${path}[${depName}];`;
          }
        );
    }
    // Every node navigates to its Module API page.
    nodeOnClickCallbacks[module[0]] = () => {
      setSelectedModule(module[0]);
      setShowDepGraph(false); // Navigate to the API.
    }
  }

  mermaidGraph += `style rootNode fill:#fff;`;
  return (
    <Mermaid
      chart={mermaidGraph}
      selectedModule={selectedModule}
      nodeOnClickCallbacks={nodeOnClickCallbacks}
    />
  )
}