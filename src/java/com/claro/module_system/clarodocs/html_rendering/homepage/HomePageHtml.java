package com.claro.module_system.clarodocs.html_rendering.homepage;

import com.claro.module_system.clarodocs.html_rendering.homepage.sidenav.SideNavHtml;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;

import java.io.InputStream;
import java.util.Scanner;
import java.util.stream.Collectors;

public class HomePageHtml {
  public static String renderHomePage(
      ImmutableMap<String, String> moduleDocsByUniqueModuleName,
      ImmutableTable<String, String, String> typeDefHtmlByModuleNameAndTypeName,
      InputStream treeJSInputStream,
      InputStream treeJSCSSInputStream) {
    String treeJS = readInputStream(treeJSInputStream);
    String treeJSCSS = readInputStream(treeJSCSSInputStream);
    return
        "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js\"></script>\n" +
        "<link rel=\"stylesheet\" href=\"https://use.fontawesome.com/releases/v5.0.13/css/all.css\" integrity=\"sha384-DNOHZ68U8hZfKXOrtjWvjxusGo9WQnrNx2sqG0tfsghAvtVlRW3tvkXWZh58N9jp\" crossorigin=\"anonymous\">\n" +
        "<style>\n" +
        treeJSCSS + "\n" +
        ".tokenGroup1 {\n" +
        "  color: #CC7832;\n" +
        "}\n" +
        ".tokenGroup2 {\n" +
        "  color: #3C85BA;\n" +
        "}\n" +
        ".tokenGroup3 {\n" +
        "  color: #0F9795;\n" +
        "}\n" +
        ".tokenGroup4 {\n" +
        "  color: #86A659;\n" +
        "}\n" +
        ".type-link {\n" +
        "  text-decoration: underline;\n" +
        "}\n" +
        ".type-link:hover {\n" +
        "  cursor: pointer;\n" +
        "  background-color: #ccc;\n" +
        "}\n" +
        ".floating-div {\n" +
        "  display: none;\n" +
        "  position: absolute;\n" +
        "  background-color: #272822;\n" +
        "  color: #f8f8f2;\n" +
        "  border: 2px dashed #ccc;\n" +
        "  font-family: monospace;\n" +
        "  padding: 10px;\n" +
        "  z-index: 999;\n" +
        "}\n" +
        ".module-docs {\n" +
        "  height: 100%;\n" +
        "  margin-left: 300px; /* Same as the width of the sidenav */\n" +
        "  font-family: monospace;\n" +
        "  white-space: pre;\n" +
        "  tab-size: 4;\n" +
        "  background-color: #272822;\n" +
        "  color: #f8f8f2;\n" +
        "  overflow-x: scroll;\n" +
        "}\n" +
        ".procedure-def {\n" +
        "  margin-left: 30px;" +
        "}\n" +
        ".initializers {\n" +
        "  margin-left: 30px;" +
        "}\n" +
        ".unwrappers {\n" +
        "  margin-left: 30px;" +
        "}\n" +
        ".contract-def {\n" +
        "  margin-left: 30px;" +
        "}\n" +
        ".initializers .procedure-def {\n" +
        "  margin-left: 60px;" +
        "}\n" +
        ".unwrappers .procedure-def {\n" +
        "  margin-left: 60px;" +
        "}\n" +
        ".contract-def .procedure-def {\n" +
        "  margin-left: 60px;" +
        "}\n" +
        "\n" +
        ".tj_container {\n" +
        "  height: 100%;\n" +
        "  width: 300px;\n" +
        "  position: fixed;\n" +
        "  z-index: 1;\n" +
        "  top: 0;\n" +
        "  left: 0;\n" +
        "  background-color: #111;\n" +
        "  overflow-x: scroll;\n" +
        "  padding-top: 20px;\n" +
        "}\n" +
        ".tj_container li {\n" +
        "  color: white;\n" +
        "}\n" +
        "@media screen and (max-height: 450px) {\n" +
        "  .sidenav {padding-top: 15px;}\n" +
        "  .sidenav a {font-size: 18px;}\n" +
        "}\n" +
        "\n" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<div class=\"floating-div\">\n" +
        "  <div id=\"type-preview\">This is the floating divThis is the floating divThis is the floating div!!!This is the floating div!</div>\n" +
        "</div>" +
        "<div class=\"tj_container\" id=\"sidenav\"></div>\n" +
        "<div class=\"module-docs\" id=\"module-view\">\n" +
        "  <h1>Choose a Module Using the Side Nav to View its ClaroDocs!</h1>\n" +
        "  <img src=\"https://raw.githubusercontent.com/JasonSteving99/claro-lang/main/logo/ClaroLogoFromArrivalHeptapodOfferWeapon1.jpeg\" width=\"60%\" height=\"auto\"></img>\n" +
        "</div>\n" +
        "<script>" + treeJS + "</script>\n" +
        "<script>\n" +
        "  function renderModule(moduleName, rootForExpansion) {\n" +
        "    console.log(`Opening ClaroDocs for Module: ${moduleName}`);\n" +
        "    $('#module-view').html(moduleContentByModuleName[moduleName]);\n" +
        "    if (rootForExpansion) {\n" +
        "      let moduleNode = nodes[moduleName];\n" +
        "      new TreePath(rootForExpansion, moduleNode).getPath().forEach(n => {console.log(n.getUserObject()); n.setExpanded(true);});\n" +
        "      view.getSelectedNodes().forEach(n => n.setSelected(false));\n" +
        "      moduleNode.setSelected(true);\n" +
        "      onMouseOutTypeLink();\n" +
        "      view.reload();\n" +
        "    }\n" +
        "  }\n" +
        // Setup the module level html map.
        "let moduleContentByModuleName = {};\n" +
        moduleDocsByUniqueModuleName.entrySet().stream()
            .map(moduleEntry ->
                     String.format(
                         "moduleContentByModuleName['%s'] = `%s`;", moduleEntry.getKey(), moduleEntry.getValue()))
            .collect(Collectors.joining("\n", "", "\n\n")) +
        // Setup the typedef level html map.
        "let typeDefContentByModuleNameAndTypeName = {};\n" +
        typeDefHtmlByModuleNameAndTypeName.rowMap().entrySet().stream()
            .map(e -> {
              String currModuleMap = String.format("typeDefContentByModuleNameAndTypeName['%s']", e.getKey());
              // Start by setting the nested map for this unique module.
              StringBuilder res = new StringBuilder(String.format("%s = {};\n", currModuleMap));
              e.getValue().forEach(
                  (typeName, typedefHtml) ->
                      res.append(String.format("%s['%s'] = `%s`;", currModuleMap, typeName, typedefHtml)));
              return res;
            })
            .collect(Collectors.joining("\n", "", "\n\n")) +
        SideNavHtml.codegenSideNavTreeSetupJS(moduleDocsByUniqueModuleName.keySet().asList()) +
        "// Listen for the mouseover event on the element.\n" +
        "const floatingDiv = document.querySelector('.floating-div');\n" +
        "  function onMouseOverTypeLink(event, uniqueModuleName, typeName) {\n" +
        "    // Swap out the html in the floating-div with the current type's def.\n" +
        "    $('#type-preview').html(typeDefContentByModuleNameAndTypeName[uniqueModuleName][typeName]);\n" +
        "    // Get the cursor position.\n" +
        "    let offset = 5;\n" +
        "    var cursorX = event.clientX + offset;\n" +
        "    var cursorY = event.clientY + offset;\n" +
        "    // Position the floating div at the cursor position.\n" +
        "    floatingDiv.style.left = cursorX + 'px';\n" +
        "    floatingDiv.style.top = cursorY + 'px';\n" +
        "\n" +
        "    // Show the floating div.\n" +
        "    floatingDiv.style.display = 'block';\n" +
        "  }\n" +
        "  function onMouseOutTypeLink(event) {\n" +
        "    floatingDiv.style.display = 'none';\n" +
        "  }\n" +
        "</script>\n" +
        "</body>\n" +
        "</html>";
  }

  private static String readInputStream(InputStream inputStream) {
    Scanner scan = new Scanner(inputStream);

    StringBuilder inputProgram = new StringBuilder();
    while (scan.hasNextLine()) {
      inputProgram.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputProgram.append("\n");
    }
    return inputProgram.toString();
  }
}
