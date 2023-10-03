package com.claro.module_system.clarodocs.html_rendering.homepage.sidenav;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class SideNavHtml {
  public static String codegenSideNavTreeSetupJS(ImmutableList<String> uniqueModuleNamesList) {
    StringBuilder codegen = new StringBuilder("var root = new TreeNode(\"\\/\\/\");\n");

    Dir rootDir =
        constructModuleDirStructure(uniqueModuleNamesList.stream().sorted().collect(ImmutableList.toImmutableList()));
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

  private static Dir constructModuleDirStructure(ImmutableList<String> uniqueModuleNamesList) {
    Dir rootDir = Dir.create("\\/\\/");
    uniqueModuleNamesList.forEach(
        mod -> addModule(rootDir, ImmutableList.copyOf(mod.split("\\$")), 0, mod)
    );
    return rootDir;
  }

  private static void addModule(Dir currDir, ImmutableList<String> modulePath, int pathInd, String uniqueModuleName) {
    String currModulePathElem = modulePath.get(pathInd);
    if (pathInd == modulePath.size() - 1) {
      currDir.getModules().put(currModulePathElem, uniqueModuleName);
    } else {
      Dir currModulePathDir = currDir.getSubDirs().build().get(currModulePathElem);
      if (currModulePathDir == null) {
        currDir.getSubDirs().put(currModulePathElem, currModulePathDir = Dir.create(modulePath.get(pathInd)));
      }
      addModule(currModulePathDir, modulePath, pathInd + 1, uniqueModuleName);
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

    public String toTreeJS(String origCurrDirName) {
      StringBuilder res = new StringBuilder();
      Dir currDir = this;
      ImmutableMap<String, Dir> subDirs = currDir.getSubDirs().build();
      StringBuilder currDirNameBuilder = new StringBuilder(origCurrDirName);
      // First things first, I want to auto-collapse any dirs that have only a single child to limit nesting to only
      // what's absolutely necessary.
      while (subDirs.size() == 1) {
        Map.Entry<String, Dir> onlySubDir = subDirs.entrySet().asList().get(0);
        currDirNameBuilder.append("/").append(onlySubDir.getKey());
        subDirs = (currDir = onlySubDir.getValue()).getSubDirs().build();
      }
      String currDirName = currDirNameBuilder.toString();
      if (!origCurrDirName.equals(currDirName)) {
        // Then I actually want to replace the node that was generated previously for the original dir name before
        // collapsing was done.
        res.append("nodes['").append(origCurrDirName).append("'].setUserObject('");
        if (origCurrDirName.equals("\\/\\/")) {
          res.append(currDirName);
        } else {
          res.append(currDirName.substring(currDirName.indexOf('$') + 1));
        }
        res.append("');\n")
            .append("nodes['").append(currDirName).append("']")
            .append(" = nodes['").append(origCurrDirName).append("'];\n");
      }


      for (Map.Entry<String, Dir> subdirEntry : subDirs.entrySet()) {
        String currSubDirName = java.lang.String.format("%s$%s", currDirName, subdirEntry.getKey());
        codegenNewTreeNodeChild(currDirName, currSubDirName, subdirEntry.getKey(), res);
        // Recursively descend into all of the subdirs first, so that they're grouped at the top above the leaf modules.
        res.append(subdirEntry.getValue().toTreeJS(currSubDirName));
      }
      for (Map.Entry<String, String> moduleEntry : currDir.getModules().build().entrySet()) {
        String currSubDirName = java.lang.String.format("%s$%s", currDirName, moduleEntry.getKey());
        codegenNewTreeNodeChild(currDirName, currSubDirName, moduleEntry.getKey(), res);
        // Register the module's onclick callback to render this content.
        res.append("nodes['").append(currSubDirName).append("']")
            .append(".on('click', () => renderModule('").append(moduleEntry.getValue()).append("'));\n");
        // Also create an alias to the new node using the unique module name so that the type-link's can reference it.
        res.append("nodes['").append(moduleEntry.getValue()).append("']")
            .append(" = nodes['").append(currSubDirName).append("'];\n");
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
