import React from 'react';
import { Tree } from 'antd';
import type { TreeDataNode, TreeProps } from 'antd';
import { DownOutlined } from '@ant-design/icons';

import { getClaroModules } from '../../claro_module_apis/demo';


const treeData: TreeDataNode[] = generateTreeData(getClaroModules(null));

export function ModuleTree({ style, selectedModule, setSelectedModule }): React.FC {
  const onSelect: TreeProps['onSelect'] = (selectedKeys, info) => {
    if (info.node.key.indexOf(':') > 0) {
      setSelectedModule(info.node.key);
    }
  };

  return (
    <Tree
      showLine
      switcherIcon={<DownOutlined />}
      defaultExpandAll={true}
      defaultSelectedKeys={[selectedModule]}
      onSelect={onSelect}
      treeData={treeData}
      style={style}
    />
  );
};

function generateTreeData(modules) {
  const treeData: TreeDataNode[] = [];

  const sortedModules =
    Object.keys(modules)
      .toSorted((m1, m2) => m1.localeCompare(m2));

  for (let target of sortedModules) {
    const path = target.split(/\/|:/).filter(s => s !== '')
    let treeLevel = treeData;
    let currKey = '/';
    for (let i = 0; i < path.length; i++) {
      const pathPart = path[i];
      currKey += `${i === path.length - 1 ? ':' : '/'}${pathPart}`;
      const existing = treeLevel.filter(e => e.title === pathPart)[0];
      if (existing) {
        treeLevel = existing.children;
      } else {
        const newEl = {
          title: pathPart,
          key: currKey,
          children: [],
          selectable: i === path.length - 1,
          isLeaf: i === path.length - 1,
        };
        treeLevel.push(newEl);
        treeLevel = newEl.children;
      }
    }
  }

  return treeData;
}