package com.claro.module_system;

import com.claro.ModuleApiLexer;
import com.claro.ModuleApiParser;
import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.ModuleNode;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.StringReader;
import java.util.Scanner;

public class TestModuleParser {
  public static void main(String... args) throws Exception {
    ModuleApiParser moduleParser = getParserForModuleApiFileOnStdIn();
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

    // TODO(steving) Consider factoring out the core functionality from the ContractProcedureSignatureDefinitionStmt so
    //   that it doesn't actually require masquerading the Module as though it's a ContractDefitionStmt.
    // Unfortunately, just for the sake of integrating w/ the existing ContractProcedureSignatureDefinitionStmt's
    // expectations of being used w/in the context of a ContractDefinitionStmt, I need to artificially masquerade as
    // though this Module definition is actually a ContractDefinitionStmt.
    InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = moduleParser.generatedClassName + "$MODULE$";
    InternalStaticStateUtil.ContractDefinitionStmt_currentContractGenericTypeParamNames =
        moduleNode.exportedSignatures.stream().map(sig -> sig.procedureName).collect(ImmutableList.toImmutableList());
    for (ContractProcedureSignatureDefinitionStmt exportedSignature : moduleNode.exportedSignatures) {
      exportedSignature.assertExpectedExprTypes(scopedHeap);
    }
    moduleNode.exportedSignatures.forEach(
        sig -> {
          System.out.println("Parsed this sig:\n\t" + sig.procedureName);
          System.out.println("\t" + sig.getExpectedProcedureTypeForConcreteTypeParams(ImmutableMap.of()));
        });
    InternalStaticStateUtil.ContractDefinitionStmt_currentContractName = null;
  }

  private static ModuleApiParser getParserForModuleApiFileOnStdIn() {
    Scanner scan = new Scanner(System.in);

    StringBuilder inputModuleApi = new StringBuilder();
    while (scan.hasNextLine()) {
      inputModuleApi.append(scan.nextLine());
      // Scanner is being stupid and dropping all the newlines... so this may give an extra compared to what's in the
      // source file, but who cares, the grammar will handle it.
      inputModuleApi.append("\n");
    }

    return createParser(inputModuleApi.toString(), "TestModule");
  }

  private static ModuleApiLexer createLexer(String input) {
    ModuleApiLexer lexer = new ModuleApiLexer(new StringReader(input));
    return lexer;
  }

  public static ModuleApiParser createParser(String input, String srcFilename) {
    ModuleApiLexer lexer = createLexer(input);
    lexer.moduleFilename = srcFilename;
    ModuleApiParser parser = new ModuleApiParser(lexer);
    parser.generatedClassName = srcFilename;
    return parser;
  }
}
