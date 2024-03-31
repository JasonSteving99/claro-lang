import { useEffect, useRef } from 'react';
import { Tooltip } from 'antd';

// This `__MODULE_DEP_GRAPH_CONFIG__` global is set by Vite as part of the --config. This uses esbuild define under the hood.
const data = __MODULE_DEP_GRAPH_CONFIG__;

export function getClaroModules(setSelectedModule) {
  const rootDeps = {};
  Object.entries(data.root.rootDeps).forEach(
    e => rootDeps[e[0]] = formatUniqueModuleName(e[1])
  )

  const claroModules = {};
  const hasDependents = new Set();
  Object.keys(data.depGraph).forEach(
    mod => {
      // Copy the data so that it's not overwritten.
      const fmtMod = formatUniqueModuleName(mod);
      claroModules[fmtMod] = {};
      claroModules[fmtMod]['api'] = data.depGraph[mod].api.trim();
      claroModules[fmtMod]['deps'] = {};

      for (let dep of Object.keys(data.depGraph[mod].deps)) {
        claroModules[fmtMod].deps[dep] = formatUniqueModuleName(data.depGraph[mod].deps[dep]);
        hasDependents.add(claroModules[fmtMod].deps[dep]);
      }
      claroModules[fmtMod].deps = getDepTooltips(claroModules[fmtMod].deps, setSelectedModule);
    }
  );

  return [claroModules, data.root.rootName, rootDeps];
}

function getDepTooltips(deps, setSelectedModule) {
  const tooltips = {};
  Object.entries(deps).forEach(
    t => tooltips[t[0]] = {path: t[1], tooltip: <DepTooltip depName={t[0]} targetModule={t[1]} setSelectedModule={setSelectedModule} />}
  );
  return tooltips;
}

function DepTooltip({ depName, targetModule, setSelectedModule }) {
  const depRef = useRef(null);
  useEffect(() => depRef.current.addEventListener(
    'click',
    () => setSelectedModule(targetModule)
  ));

  const title = <span>{targetModule}</span>;
  return (
      <Tooltip className='clickable' placement="top" title={title}>
        <u ref={depRef}>{depName}</u>
      </Tooltip>
    );
}

function formatUniqueModuleName(uniqueModuleName) {
  let res = '//' + uniqueModuleName.replaceAll('$', '/');
  const colonInd = res.lastIndexOf('/');
  return res.substring(0, colonInd) + ':' + res.substring(colonInd + 1);
}