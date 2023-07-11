package com.claro.module_system;

import com.claro.ModuleApiParser;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.ModuleNode;

public class TestModuleParser {
  public static void main(String... args) throws Exception {
    ModuleApiParser moduleParser = ModuleApiParserUtil.getParserForModuleApiFileOnStdIn();
    System.out.println("Going to parse the module on stdin...");
    ModuleNode moduleNode;
    try {
      moduleNode = (ModuleNode) moduleParser.parse().value;
    } catch (Exception e) {
      System.err.println("Encountered parsing errors: " + moduleParser.errorsFound);
      ModuleApiParser.errorMessages.forEach(Runnable::run);
      System.exit(1);
      return;
    }
    System.out.println("Done parsing...");

    ScopedHeap scopedHeap = new ScopedHeap();
    scopedHeap.enterNewScope();

    moduleNode.getExportedProcedureSignatureTypes(scopedHeap).forEach(
        (procName, procType) -> {
          System.out.println("Parsed this sig:\n\t" + procName);
          System.out.println("\t" + procType);
        });
  }
}
