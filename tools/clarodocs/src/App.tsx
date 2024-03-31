/// <reference types="vite-plugin-svgr/client" />
import './App.css';

import React, { useEffect, useState } from 'react';
import { getClaroModules, getRootDeps } from './claro_module_apis/demo';
import { HighlightJS } from './components/highlight_js/HighlightJS';
import { DepGraph } from './components/dep_graph/DepGraph';
import { WholeProgramDepGraph } from './components/dep_graph/WholeProgramDepGraph';
import { ModuleTree } from './components/module_tree/ModuleTree';
import {
  createBrowserRouter,
  RouterProvider,
  useSearchParams
} from "react-router-dom";
// import { useSearchParamsState } from './hooks/UseSearchParamsHook';

import { Breadcrumb, ConfigProvider, Layout, Menu, theme } from 'antd';
const { Header, Sider, Content } = Layout;

function AppImpl() {
  const [ searchParams, setSearchParams ] = useSearchParams();

  const showDepGraph = searchParams.get('showDepGraph') === null || searchParams.get('showDepGraph') === 'true';
  let selectedModule = searchParams.get('selectedModule') || '';
  const targetType = searchParams.get('targetType') || '';

  const setShowDepGraph = function() {
    setSearchParams({
      showDepGraph: !showDepGraph,
      selectedModule: '',
      targetType: ''
    });
  }
  const setSelectedModule = function(newSelectedModule) {
    setSearchParams({
      showDepGraph: false,
      selectedModule: newSelectedModule,
      targetType: targetType
    });
  }
  const setSelectedModuleAndTargetType = function(newSelectedModule, newTargetType) {
    setSearchParams({
      showDepGraph: false,
      selectedModule: newSelectedModule,
      targetType: newTargetType
    });
  }
  const setTargetType = function(newTargetType) {
    setSearchParams({
      showDepGraph: showDepGraph,
      selectedModule: selectedModule,
      targetType: newTargetType
    });
  }

  const [ ClaroModules, rootName, rootDeps ] = getClaroModules(setSelectedModule);

  useEffect(
    () => {
      async function stopBlinking() {
        await new Promise(r => setTimeout(r, 3000));
        const targetTypeSpan = document.querySelector(".blink");
        targetTypeSpan.classList.remove("blink");
        setTargetType('');
      }
      if (targetType) {
        stopBlinking();
      }
    },
    [targetType]
  );

  function APIs() {
    if (!selectedModule) {
      // Ensure that this still works even if the user just clicked the APIs tab directly without manually selecting a
      // module.
      selectedModule = Object.keys(ClaroModules).toSorted()[0];
    }

    // We'll reuse these tooltip components every time the dep is referenced in the Module API.
    const selectedModuleDepsTooltips = ClaroModules[selectedModule]?.deps;

    const {
      token: { colorBgContainer, borderRadiusLG },
    } = theme.useToken();

    return (
      <Layout hasSider>
        <React.StrictMode>
          <Sider style={{  minHeight: '92vh' }}>
            <h1 style={{ float: "top", color: "white" }}>Modules</h1>
            <ModuleTree
              selectedModule={selectedModule}
              setSelectedModule={setSelectedModule}
              style={{ height: '80vh', overflow: 'scroll' }}
            />
          </Sider>
        </React.StrictMode>
        <Layout>
          <Content
            style={{
              margin: '24px 16px',
              padding: 24,
              minHeight: 280,
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
            }}
          >
            <React.StrictMode>
              <ConfigProvider
                theme={{
                  token: {
                    fontSize: '30px',
                  },
                }}
              >
                <Breadcrumb
                  items={
                    selectedModule
                      .split(/\/|:/)
                      .filter(s => s !== '')
                      .reduce(
                        (accum, pathPart) => {
                          accum.pathPrefix += `/${pathPart}`;
                          const item = { title: pathPart, className: 'breadcrumb-dropdown' };

                          // This breadcrumb part only needs to become a menu *iff* there's other modules at this level.
                          const siblingModules =
                            Object.keys(ClaroModules)
                              .filter(m => m.startsWith(`${accum.pathPrefix}:`) && m !== selectedModule);
                          if (siblingModules.length > 0) {
                            const pathPrefix = accum.pathPrefix;
                            item.menu = {
                                items: siblingModules.toSorted().map(
                                  sibling => {
                                    var siblingName = sibling.slice(sibling.indexOf(':') + 1);
                                    return {key: siblingName, label: siblingName};
                                  }),
                                onClick: ({key, domEvent}) => {
                                  setSelectedModule(`${pathPrefix}:${domEvent.target.textContent}`);
                                }
                              };
                          }

                          accum.menuItems.push(item);
                          return accum;
                        },
                        { pathPrefix: '/', menuItems: []} // accum
                      ).menuItems
                  }
                />
              </ConfigProvider>
            </React.StrictMode>
            <DepGraph
              selectedModule={selectedModule}
              deps={ClaroModules[selectedModule]?.deps}
              dependents={
                Object.entries(ClaroModules)
                  .map(
                    ([dependent, { deps }]) => {
                      let selectedModuleDep = Object.entries(deps).filter(([, { path }]) => path === selectedModule);
                      if (selectedModuleDep.length > 0) {
                        // Return the name that the dependent uses for selectedModule.
                        return {dependentName: dependent, depName: selectedModuleDep[0][0]};
                      }
                      return null;
                    })
                  .filter(d => d !== null)
              }
              setSelectedModule={setSelectedModule}
            />
            <HighlightJS
              className="claro"
              style={{ textAlign: "left" }}
              id={selectedModule}
              targetType={targetType}
              setSelectedMOduleAndTargetType={setSelectedModuleAndTargetType}
              selectedModuleDepsTooltips={selectedModuleDepsTooltips}
            >
              {ClaroModules[selectedModule]?.api}
            </HighlightJS>
          </Content>
        </Layout>
      </Layout>
    );
  }

  function ProgramDepGraphVisualization() {
    return (
      <WholeProgramDepGraph
        rootName={rootName}
        rootDeps={rootDeps}
        modules={ClaroModules}
        selectedModule={selectedModule}
        setSelectedModule={setSelectedModule}
      />
    );
  }

  const headerNavItems: MenuProps['items'] = ['Dep Graph', 'APIs'].map((key) => ({
    key,
    label: key,
  }));

  const content = showDepGraph ? <ProgramDepGraphVisualization /> : <APIs />;

  // Unfortunately I have to manually enforce "StrictMode" on everything *except for the HighlightJS* component since
  // this one component is manually editing the DOM in order to use HighlightJS's parsing and auto-highlighting
  // functionality.
  return (
    <div className="App" style={{ display: "flex", width: "100vw" }}>
      <Layout>
        <Header style={{ display: 'flex', height: '12vh' }}>
          <div className="demo-logo" />
          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[showDepGraph ? 'Dep Graph' : 'APIs']}
            items={headerNavItems}
            onClick={({ key }) => setShowDepGraph()}
            style={{ flex: 1, minWidth: 200 }}
          />
        </Header>
        <Content>
          {content}
        </Content>
      </Layout>
    </div>
  );
}

function App() {
  // We'll wrap everything in a React Router literally just so that it can easily maintain state from query params.
  // The useSearchParams() function simply doesn't work outside of this RouterProvider context. Lame.
  const router = createBrowserRouter([
    {
      path: "/",
      element: <AppImpl />,
    },
  ]);

  return <RouterProvider router={router} />
}

export default App;