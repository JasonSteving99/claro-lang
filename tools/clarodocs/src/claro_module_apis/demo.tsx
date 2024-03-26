import { useEffect, useRef } from 'react';
import { Tooltip } from 'antd';
import data from './testjson.json' assert { type: 'json' };

export function getClaroModules(setSelectedModule) {
  const claroModules = {};
  const hasDependents = new Set();
  Object.keys(data).forEach(
    mod => {
      // Copy the data so that it's not overwritten.
      const fmtMod = formatUniqueModuleName(mod);
      claroModules[fmtMod] = {};
      claroModules[fmtMod]['api'] = data[mod].api;
      claroModules[fmtMod]['deps'] = {};

      for (let dep of Object.keys(data[mod].deps)) {
        claroModules[fmtMod].deps[dep] = formatUniqueModuleName(data[mod].deps[dep]);
        hasDependents.add(claroModules[fmtMod].deps[dep]);
      }
      claroModules[fmtMod].deps = getDepTooltips(claroModules[fmtMod].deps, setSelectedModule);
    }
  );
  const rootDeps = Object.keys(claroModules).filter(m => !(m in hasDependents));
  return [claroModules, rootDeps];
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