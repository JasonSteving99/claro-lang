package com.claro.module_system.clarodocs.html_rendering.homepage.sidenav;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class SideNavHtml {
  public static String codegenSideNavTreeSetupJS(ImmutableMap<String, String> moduleDocsByUniqueModuleName) {
    StringBuilder codegen = new StringBuilder(
        "let moduleContentByModuleName = {};\n" +
        "var root = new TreeNode(\"\\/\\/\");\n"
    );

    Dir rootDir = constructModuleDirStructure(
        moduleDocsByUniqueModuleName.keySet().stream().sorted().collect(ImmutableList.toImmutableList()),
        moduleDocsByUniqueModuleName
    );
    codegen.append("nodes = {'\\/\\/': new TreeNode('\\/\\/')};\n");
    codegen.append(rootDir.toTreeJS("\\/\\/"));

    return codegen.append(
        "root = nodes['\\/\\/'];\n" +
        "var view = new TreeView(root, \"#sidenav\");\n" +
        "view.changeOption(\"leaf_icon\", '<i class=\"fas fa-file\"></i>');\n" +
        "view.changeOption(\"parent_icon\", '<i class=\"fas fa-folder\"></i>');\n" +
        "TreeConfig.open_icon = '<i class=\"fas fa-angle-down\"></i>';\n" +
        "TreeConfig.close_icon = '<i class=\"fas fa-angle-right\"></i>';\n" +
        "view.collapseAllNodes();\n" +
        "root.toggleExpanded();\n" +
        "view.reload();\n"
    ).toString();
  }

  private static Dir constructModuleDirStructure(
      ImmutableList<String> uniqueModuleNamesList,
      ImmutableMap<String, String> moduleDocsByUniqueModuleName) {
    Dir rootDir = Dir.create("\\/\\/");
    uniqueModuleNamesList.forEach(
        mod -> addModule(rootDir, ImmutableList.copyOf(mod.split("\\$")), 0, moduleDocsByUniqueModuleName.get(mod))
    );
    return rootDir;
  }

  private static void addModule(Dir currDir, ImmutableList<String> modulePath, int pathInd, String moduleDocs) {
    String currModulePathElem = modulePath.get(pathInd);
    if (pathInd == modulePath.size() - 1) {
      currDir.getModules().put(currModulePathElem, moduleDocs);
    } else {
      Dir currModulePathDir = currDir.getSubDirs().build().get(currModulePathElem);
      if (currModulePathDir == null) {
        currDir.getSubDirs().put(currModulePathElem, currModulePathDir = Dir.create(modulePath.get(pathInd)));
      }
      addModule(currModulePathDir, modulePath, pathInd + 1, moduleDocs);
    }
  }

  @AutoValue
  public static abstract class Dir {
    public abstract ImmutableMap.Builder<String, Dir> getSubDirs();

    // name -> unique_module_name
    public abstract ImmutableMap.Builder<String, String> getModules();

    public static Dir create(String name) {
      return new AutoValue_SideNavHtml_Dir(ImmutableMap.builder(), ImmutableMap.builder());
    }

    public String toTreeJS(String currDirName) {
      StringBuilder res = new StringBuilder();
      for (Map.Entry<String, Dir> subdirEntry : this.getSubDirs().build().entrySet()) {
        String currSubDirName = java.lang.String.format("%s$%s", currDirName, subdirEntry.getKey());
        codegenNewTreeNodeChild(currDirName, currSubDirName, subdirEntry.getKey(), res);
        // Recursively descend into all of the subdirs first, so that they're grouped at the top above the leaf modules.
        res.append(subdirEntry.getValue().toTreeJS(currSubDirName));
      }
      for (Map.Entry<String, String> moduleEntry : this.getModules().build().entrySet()) {
        String currSubDirName = java.lang.String.format("%s$%s", currDirName, moduleEntry.getKey());
        codegenNewTreeNodeChild(currDirName, currSubDirName, moduleEntry.getKey(), res);
        // Register the module content and an onclick callback to render this content.
        res.append("moduleContentByModuleName['").append(currSubDirName).append("'] ")
            .append("= `").append(moduleEntry.getValue()).append("`;\n");
        res.append("nodes['").append(currSubDirName).append("']")
            .append(".on('click', () => renderModule('").append(currSubDirName).append("'));\n");
      }
      return res.toString();
    }

    private static void codegenNewTreeNodeChild(
        String currDirName, String currSubDirName, String subdirShortName, StringBuilder res) {
      res.append("nodes['").append(currSubDirName).append("'] ")
          .append("= new TreeNode(\"").append(subdirShortName).append("\");\n");
      res.append("nodes['").append(currDirName).append("'].addChild(nodes['").append(currSubDirName).append("']);\n");
    }
  }
}
