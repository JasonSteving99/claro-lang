package com.claro.module_system.clarodocs.html_rendering.homepage;

import com.claro.module_system.clarodocs.html_rendering.homepage.sidenav.SideNavHtml;
import com.google.common.collect.ImmutableMap;

import java.io.InputStream;
import java.util.Scanner;

public class HomePageHtml {
  public static String renderHomePage(
      ImmutableMap<String, String> moduleDocsByUniqueModuleName,
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
        ".module-docs {\n" +
        "  margin-left: 300px; /* Same as the width of the sidenav */\n" +
        "  font-family: monospace;\n" +
        "  white-space: pre;\n" +
        "  tab-size: 4;\n" +
        "  background-color: #272822;\n" +
        "  color: #f8f8f2;\n" +
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
        "<div class=\"tj_container\" id=\"sidenav\"></div>\n" +
        "<div class=\"module-docs\" id=\"module-view\">\n" +
        "  <h1>Choose a Module Using the Side Nav to View its ClaroDocs!</h1>\n" +
        "  <img src=\"https://raw.githubusercontent.com/JasonSteving99/claro-lang/main/logo/ClaroLogoFromArrivalHeptapodOfferWeapon1.jpeg\" width=\"60%\" height=\"auto\"></img>\n" +
        "</div>\n" +
        "<script>" + treeJS + "</script>\n" +
        "<script>\n" +
        "  function renderModule(moduleName) {\n" +
        "    console.log(`Called the func ${moduleName}`);\n" +
        "    // Try swapping out the html for the module-docs div.\n" +
        "\tlet newElem = moduleContentByModuleName[moduleName];\n" +
        "    $('#module-view').html(newElem);\n" +
        "    console.log($('#module-view'));\n" +
        "    console.log(moduleContentByModuleName[moduleName]);\n" +
        "    console.log(moduleContentByModuleName);\n" +
        "  }\n" +
        SideNavHtml.codegenSideNavTreeSetupJS(moduleDocsByUniqueModuleName) +
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
