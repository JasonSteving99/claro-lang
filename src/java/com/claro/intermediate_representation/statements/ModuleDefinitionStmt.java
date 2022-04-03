package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.claro.runtime_utilities.injector.Injector;
import com.claro.runtime_utilities.injector.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModuleDefinitionStmt extends Stmt {
  private final String moduleName;
  // TODO(steving) Support Module Composition!
  private final Optional<ImmutableList<String>> usedModuleNameList;
  private final StmtListNode stmtListNode;
  public ImmutableSet<Key> boundKeySet;

  public ModuleDefinitionStmt(String moduleName, StmtListNode stmtListNode) {
    this(moduleName, Optional.empty(), stmtListNode);
  }

  public ModuleDefinitionStmt(
      String moduleName, Optional<ImmutableList<String>> usedModuleNameList, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.moduleName = moduleName;
    this.usedModuleNameList = usedModuleNameList;
    this.stmtListNode = stmtListNode;
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Here, we need to both assert that the binding is typed correctly to its bound Expr, but we also
    // need to validate that none of the Keys in this module are conflicting. We don't need global
    // uniqueness on names, but we do need global uniqueness across all Keys (name + type).
    HashSet<Key> boundKeySetBuilder = new HashSet<>();
    HashSet<Key> duplicateKeySet = new HashSet<>();
    StmtListNode curr = stmtListNode;
    do {
      BindStmt bindStmt = (BindStmt) curr.getChildren().get(0);
      bindStmt.assertExpectedExprTypes(scopedHeap);
      Key key = new Key(bindStmt.name, bindStmt.type);
      if (!boundKeySetBuilder.add(key)) {
        // This is a duplicate key binding.
        duplicateKeySet.add(key);
      }
    } while ((curr = curr.tail) != null);
    if (!duplicateKeySet.isEmpty()) {
      // TODO(steving) Need to bubble up source code information for BindStmt for better error messages.
      throw ClaroTypeException.forDuplicateKeyBindings(duplicateKeySet);
    }

    // It's possible that this module actually depends on

    this.boundKeySet = ImmutableSet.copyOf(boundKeySetBuilder);

    // We need to ensure that this Module is registered, and is unique.
    if (Injector.definedModulesByNameMap.containsKey(this.moduleName)) {
      throw ClaroTypeException.forDuplicateModuleDefinition(this.moduleName);
    } else {
      Injector.definedModulesByNameMap.put(this.moduleName, this.boundKeySet);
    }

  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    // Since we'll generate a function just as a convenience, we want to turn off unused checking for now.
    boolean unusedChecking = scopedHeap.checkUnused;
    scopedHeap.checkUnused = false;

    // We'll just defer to generating a function by the moduleName that will be called when the module is used.
    ConsumerFunctionDefinitionStmt initConsumerFunctionDefinitionStmt =
        new ConsumerFunctionDefinitionStmt(
            String.format("$%s_init", moduleName),
            ImmutableMap.of("$unused", TypeProvider.ImmediateTypeProvider.of(Types.STRING)),
            this.stmtListNode
        );
    ConsumerFunctionDefinitionStmt teardownConsumerFunctionDefinitionStmt = getSyntheticTeardownFn();

    initConsumerFunctionDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
    teardownConsumerFunctionDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
    try {
      initConsumerFunctionDefinitionStmt.assertExpectedExprTypes(scopedHeap);
      teardownConsumerFunctionDefinitionStmt.assertExpectedExprTypes(scopedHeap);
    } catch (Exception e) {
      throw new IllegalStateException("Internal Compiler Error: Internal reuse of ConsumerFunctionDefinitionStmt for ModuleDefinitionStmt is broken.", e);
    }

    GeneratedJavaSource initJavaSource = initConsumerFunctionDefinitionStmt.generateJavaSourceOutput(scopedHeap);
    GeneratedJavaSource teardownJavaSource =
        teardownConsumerFunctionDefinitionStmt.generateJavaSourceOutput(scopedHeap);

    // Jump through hoops in this terrible GeneratedJavaSource API to get a single output.
    GeneratedJavaSource res =
        GeneratedJavaSource.create(
            initJavaSource.javaSourceBody().append(teardownJavaSource.javaSourceBody()),
            initJavaSource.optionalStaticDefinitions()
                .get()
                .append(teardownJavaSource.optionalStaticDefinitions().get()),
            initJavaSource.optionalStaticPreambleStmts()
                .get()
                .append(teardownJavaSource.optionalStaticPreambleStmts().get())
        );

    scopedHeap.checkUnused = unusedChecking;
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // We'll just defer to generating a function by the moduleName that will be called when the module is used.
    ConsumerFunctionDefinitionStmt initConsumerFunctionDefinitionStmt =
        new ConsumerFunctionDefinitionStmt(
            String.format("$%s_init", moduleName),
            ImmutableMap.of("$unused", TypeProvider.ImmediateTypeProvider.of(Types.STRING)),
            stmtListNode
        );
    ConsumerFunctionDefinitionStmt teardownConsumerFunctionDefinitionStmt = getSyntheticTeardownFn();

    // Jump through hoops to prepare these functions (I don't want to short circuit these because
    // I don't want this dogfooding of the ConsumerFunctionDefinitionStmt to drift in stability over time.
    initConsumerFunctionDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
    teardownConsumerFunctionDefinitionStmt.registerProcedureTypeProvider(scopedHeap);
    try {
      initConsumerFunctionDefinitionStmt.assertExpectedExprTypes(scopedHeap);
      teardownConsumerFunctionDefinitionStmt.assertExpectedExprTypes(scopedHeap);
    } catch (Exception e) {
      throw new IllegalStateException("Internal Compiler Error: Internal reuse of ConsumerFunctionDefinitionStmt for ModuleDefinitionStmt is broken.", e);
    }

    // Finally execute the logic to put these in the ScopedHeap for later use.
    initConsumerFunctionDefinitionStmt.generateInterpretedOutput(scopedHeap);
    teardownConsumerFunctionDefinitionStmt.generateInterpretedOutput(scopedHeap);

    return null;
  }

  private ConsumerFunctionDefinitionStmt getSyntheticTeardownFn() {
    return new ConsumerFunctionDefinitionStmt(
        String.format("$%s_teardown", moduleName),
        ImmutableMap.of("$unused", TypeProvider.ImmediateTypeProvider.of(Types.STRING)),
        new StmtListNode(new Stmt(ImmutableList.of()) {
          @Override
          public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
            // Intentionally empty, this is a hardcoded procedure, there's nothing to go wrong here....right?...lol
          }

          @Override
          public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
            return GeneratedJavaSource.forJavaSourceBody(
                new StringBuilder(
                    ModuleDefinitionStmt.this.boundKeySet.stream()
                        .map(
                            key -> String.format("Injector.bindings.remove(new Key(\"%s\", %s));", key.name, key.type.getJavaSourceClaroType()))
                        .collect(Collectors.joining("\n")))
                    .append("\n")
            );
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            ModuleDefinitionStmt.this.boundKeySet.forEach(key -> Injector.bindings.remove(key));
            return null;
          }
        })
    );
  }
}
