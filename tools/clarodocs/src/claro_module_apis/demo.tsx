import { useEffect, useRef } from 'react';
import { Tooltip } from 'antd';

let setSelectedModule = null;

export function getClaroModules(_setSelectedModule) {
  setSelectedModule = _setSelectedModule;
  return {
    '//src:some_dep': {
        api: `
          newtype Bar : struct {some: int, stuff: string}

          provider getBar() -> Bar;
          `,
        deps: getDepTooltips({
          'Util': '//src/utils:util',
        })
      },
    '//src:demo': {
        api: `
          # Some newtype declaration for a type called Foo.
          newtype Foo : Dep::Bar

          function getFoo(i: int) -> Foo;
          `,
        deps: getDepTooltips({
          'Dep': '//src:some_dep',
          'Util': '//src/utils:util',
        })
      },
    '//src/utils:util': {
        api: `
          opaque newtype mut Util

          provider getUtil() -> Util;
          consumer doThingWithUtil(util: Util);
          `,
        deps: getDepTooltips({})
      },
  };
}


function getDepTooltips(deps) {
  const tooltips = {};
  Object.entries(deps).forEach(
    t => tooltips[t[0]] = {path: t[1], tooltip: <DepTooltip depName={t[0]} targetModule={t[1]} />}
  );
  return tooltips;
}

function DepTooltip({ depName, targetModule }) {
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