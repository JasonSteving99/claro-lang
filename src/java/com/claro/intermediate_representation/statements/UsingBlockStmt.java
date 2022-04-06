package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.StringTerm;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UsingBlockStmt extends Stmt {
  // This set is maintained in order to allow for nested using blocks while still validating that no
  // bindings that are *already* being used before the nested block are re-bound.
  public static Set<Key> currentlyUsedBindings = Sets.newHashSet();
  public static Set<String> currentlyUsedModules = Sets.newHashSet();

  private ImmutableList<String> moduleNameList;
  private final StmtListNode stmtListNode;

  public UsingBlockStmt(ImmutableList<String> moduleNameList, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.moduleNameList = moduleNameList;
    this.stmtListNode = stmtListNode;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // First validate that all module names are valid Modules and that the same Module isn't repeated.
    HashSet<String> uniqueUsedModules = Sets.newHashSetWithExpectedSize(moduleNameList.size());
    ImmutableList.Builder<String> duplicatedUsedModulesBuilder = ImmutableList.builder();
    ImmutableList.Builder<String> undefinedModulesListBuilder = ImmutableList.builder();
    for (String usedModuleName : this.moduleNameList) {
      if (!uniqueUsedModules.add(usedModuleName)) {
        duplicatedUsedModulesBuilder.add(usedModuleName);
      }
      if (!scopedHeap.isIdentifierDeclared(usedModuleName)) {
        undefinedModulesListBuilder.add(usedModuleName);
      }
    }
    ImmutableList<String> duplicatedUsedModules = duplicatedUsedModulesBuilder.build();
    if (!duplicatedUsedModules.isEmpty()) {
      throw ClaroTypeException.forUsingDuplicateModules(duplicatedUsedModules);
    }
    ImmutableList<String> undefinedModulesList = undefinedModulesListBuilder.build();
    if (!undefinedModulesList.isEmpty()) {
      throw ClaroTypeException.forUsingUndefinedModules(undefinedModulesList);
    }

    // Now, if we're in a nested using block, we should check if any of the modules used in this nested block are
    // already being used by the outer block. If so, those modules should be treated as no-ops. No-op rather than
    // an error because I don't want to create some distinction between explicitly named modules and modules that
    // are transitively used. You could argue that explicitly naming the same module in an outer and nested block
    // is meaningless and dumb, but then it has the same effect as a transitive module getting reused and short-
    // circuited, so I'll just leave it to the programmer to make this as clean as possible.
    moduleNameList = this.moduleNameList.stream()
        .filter(moduleName -> !UsingBlockStmt.currentlyUsedModules.contains(moduleName))
        .collect(ImmutableList.toImmutableList());

    // Now go through each used Module and validate that none redeclare the same bindings.
    Set<Key> duplicatedKeyBindingsSet = Sets.newHashSet();
    Set<Key> boundKeysSet =
        Sets.newHashSet(((ModuleDefinitionStmt) scopedHeap.getIdentifierValue(moduleNameList.get(0))).boundKeySet);
    for (int i = 1; i < moduleNameList.size(); i++) {
      // One by one, validate that the union between successive bound keys sets is empty, and then accumulate.
      Set<Key> nextModuleKeySet =
          ((ModuleDefinitionStmt) scopedHeap.getIdentifierValue(moduleNameList.get(i))).boundKeySet;
      Sets.SetView<Key> currDuplicates = Sets.intersection(boundKeysSet, nextModuleKeySet);
      if (currDuplicates.size() != 0) {
        currDuplicates.copyInto(duplicatedKeyBindingsSet);
      }
      boundKeysSet.addAll(nextModuleKeySet);
    }
    if (duplicatedKeyBindingsSet.size() != 0) {
      throw ClaroTypeException.forDuplicateKeyBindings(duplicatedKeyBindingsSet);
    }

    // Finally, in the case that this is a nested using-block ensure that we won't allow any re-bindings
    // for keys that were already bound by an outer using-block.
    if (!UsingBlockStmt.currentlyUsedBindings.isEmpty()) {
      Set<Key> reboundKeys = Sets.intersection(UsingBlockStmt.currentlyUsedBindings, boundKeysSet);
      if (!reboundKeys.isEmpty()) {
        throw ClaroTypeException.forRebindingKeyFromOuterUsingBlock(reboundKeys);
      }
    }

    UsingBlockStmt.currentlyUsedModules.addAll(this.moduleNameList);
    UsingBlockStmt.currentlyUsedBindings.addAll(boundKeysSet);

    // And at last we can do type validation on the StmtListNode nested in this using-block.
    this.stmtListNode.assertExpectedExprTypes(scopedHeap);

    // Now we've left this using-block scope, we need to remove all of the modules and keys that we just bound.
    UsingBlockStmt.currentlyUsedBindings.removeAll(boundKeysSet);
    UsingBlockStmt.currentlyUsedModules.removeAll(this.moduleNameList);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // First thing, we need to initialize all of the bindings that are specified by our Modules.
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder("\n/////////////////////\n// Begin using-block\n")
                .append(
                    this.moduleNameList.stream()
                        .map(moduleName -> String.format("$%s_init.apply(\"unused\");", moduleName))
                        .collect(Collectors.joining("\n")))
                .append("\n////////////////////\n// Using-block body\n")
        );

    // We then defer off to the StmtListNode encapsulated in this using block.
    GeneratedJavaSource stmtListNodeGenJavaSource = this.stmtListNode.generateJavaSourceOutput(scopedHeap);

    // Teardown the used bindings as we leave the using-block scope.
    res = GeneratedJavaSource.create(
        res.javaSourceBody()
            .append(stmtListNodeGenJavaSource.javaSourceBody())
            .append("///////////////////\n// End using-block\n")
            .append(
                this.moduleNameList.stream()
                    .map(moduleName -> String.format("$%s_teardown.apply(\"unused\");", moduleName))
                    .collect(Collectors.joining("\n")))
            .append("\n\n"),
        stmtListNodeGenJavaSource.optionalStaticDefinitions(),
        stmtListNodeGenJavaSource.optionalStaticPreambleStmts()
    );

    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // First initialize all used modules.
    this.moduleNameList.forEach(
        moduleName ->
            ((Types.ProcedureType.ProcedureWrapper)
                 scopedHeap.getIdentifierValue(String.format("$%s_init", moduleName)))
                .apply(
                    ImmutableList.of(
                        new StringTerm("unused", null, -1, -1, -1)),
                    scopedHeap
                )
    );

    // Execute the body of the using-block.
    this.stmtListNode.generateInterpretedOutput(scopedHeap);

    // Teardown the used bindings as we leave the using-block scope.
    this.moduleNameList.forEach(
        moduleName ->
            ((Types.ProcedureType.ProcedureWrapper)
                 scopedHeap.getIdentifierValue(String.format("$%s_teardown", moduleName)))
                .apply(
                    ImmutableList.of(
                        new StringTerm("unused", null, -1, -1, -1)),
                    scopedHeap
                )
    );

    return null;
  }
}
