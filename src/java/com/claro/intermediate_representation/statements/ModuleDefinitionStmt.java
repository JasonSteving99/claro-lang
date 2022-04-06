package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.StringTerm;
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
  private final Optional<ImmutableList<String>> optionalUsedModuleNameList;
  private StmtListNode stmtListNode;
  public ImmutableSet<Key> boundKeySet;
  private boolean alreadyAssertedTypes = false;
  private Optional<ImmutableSet<String>> optionalTransitiveClosureUsedModuleNameSet;

  public ModuleDefinitionStmt(String moduleName, StmtListNode stmtListNode) {
    this(moduleName, Optional.empty(), stmtListNode);
  }

  public ModuleDefinitionStmt(
      String moduleName, Optional<ImmutableList<String>> optionalUsedModuleNameList, StmtListNode stmtListNode) {
    super(ImmutableList.of());
    this.moduleName = moduleName;
    this.optionalUsedModuleNameList = optionalUsedModuleNameList;
    this.stmtListNode = stmtListNode;
  }

  // We need to do two passes over the ModuleDefinitionStmts first to validate and register all of
  // their relative bindings, so that later we can validate the using statements of all Modules.
  public void registerAssertedBoundKeys(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Here, we need to both assert that the binding is typed correctly to its bound Expr, but we also
    // need to validate that none of the Keys in this module are conflicting. We don't need global
    // uniqueness on names, but we do need global uniqueness across all Keys (name + type).
    HashSet<Key> boundKeySetBuilder = new HashSet<>();
    HashSet<Key> duplicateKeySet = new HashSet<>();
    StmtListNode curr = stmtListNode;
    do {
      BindStmt bindStmt = (BindStmt) curr.getChildren().get(0);
      bindStmt.registerBindingType(scopedHeap);
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

    this.boundKeySet = ImmutableSet.copyOf(boundKeySetBuilder);

    // We need to ensure that this Module is registered, and is unique.
    if (scopedHeap.isIdentifierDeclared(this.moduleName)) {
      throw ClaroTypeException.forDuplicateModuleDefinition(this.moduleName);
    } else {
      // We need to put a reference to this module instance in the ScopedHeap during this pre-processing phase
      // so that the type checking pass can recursively trace Module deps regardless of the order of Module defs
      // in the source code.
      scopedHeap.putIdentifierValue(this.moduleName, Types.MODULE, this);
      // It's not useful to warn on unused modules, since unused modules are inherently not even a true part
      // of the resulting program.
      scopedHeap.markIdentifierUsed(this.moduleName);
    }
  }

  // TODO(steving) Currently the sets of transitive binding keys are recomputed unnecessarily leading to potential
  //  N^2 performance..need to optimize this to make each module provide its own set of transitive bindings in
  //  the same cached approach that's being followed for transitive used modules.
  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    if (!this.alreadyAssertedTypes) {
      ImmutableSet.Builder<String> optionalTransitiveClosureUsedModuleNameSetBuilder = ImmutableSet.builder();

      if (this.optionalUsedModuleNameList.isPresent()) {
        // Trust the transitive deps reported by direct deps.
        for (String depModuleName : this.optionalUsedModuleNameList.get()) {
          // Add the direct deps no matter what, though this may turn out not to be a valid Module name in which
          // case we'll identify that when we delegate to the synthetic UsingBlockStmt.
          optionalTransitiveClosureUsedModuleNameSetBuilder.add(depModuleName);

          // Do some work to figure out the transitive deps this Module will pull in from this current depModule.
          if (scopedHeap.isIdentifierDeclared(depModuleName)) {
            ModuleDefinitionStmt usedModule =
                ((ModuleDefinitionStmt) scopedHeap.getIdentifierValue(depModuleName));

            // Run the depModule's assertions so that it prepares its transitive dep modules for us.
            // Note that it's maybe not obvious that this is the recursive bit of this process.
            usedModule.assertExpectedExprTypes(scopedHeap);

            usedModule.optionalTransitiveClosureUsedModuleNameSet.ifPresent(
                optionalTransitiveClosureUsedModuleNameSetBuilder::addAll);
          }
        }

        this.optionalTransitiveClosureUsedModuleNameSet =
            Optional.of(optionalTransitiveClosureUsedModuleNameSetBuilder.build());

        new UsingBlockStmt(
            ImmutableList.<String>builder()
                .add(this.moduleName)
                .addAll(optionalTransitiveClosureUsedModuleNameSet.get())
                .build(),
            this.stmtListNode
        ).assertExpectedExprTypes(scopedHeap);

        // Wrap the list of BindStmts with a Stmt to first defer to the init function of the used Modules.
        this.stmtListNode =
            new StmtListNode(
                new Stmt(ImmutableList.of()) {
                  @Override
                  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
                    // Synthetic node, can't fail.
                  }

                  @Override
                  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
                    return GeneratedJavaSource.forJavaSourceBody(
                        new StringBuilder(
                            ModuleDefinitionStmt.this.optionalUsedModuleNameList.get().stream()
                                .map(depModuleName -> String.format("$%s_init.apply(\"unused\");\n", depModuleName))
                                .collect(Collectors.joining())));
                  }

                  @Override
                  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
                    ModuleDefinitionStmt.this.optionalUsedModuleNameList.get().forEach(
                        depModuleName ->
                            ((Types.ProcedureType.ProcedureWrapper)
                                 scopedHeap.getIdentifierValue(String.format("$%s_init", depModuleName)))
                                .apply(
                                    ImmutableList.of(
                                        new StringTerm("unused", null, -1, -1, -1)),
                                    scopedHeap
                                ));
                    return null;
                  }
                },
                this.stmtListNode
            );
      } else {
        // This module has no deps at all so there's nothing to do other than assert types on the bindings directly.
        this.stmtListNode.assertExpectedExprTypes(scopedHeap);
        this.optionalTransitiveClosureUsedModuleNameSet = Optional.empty();
      }
    }

    // Since there's recursion over a DAG of Module defs going on, this node may be depended on from multiple paths,
    // so make sure that we never redo the work from here onwards..
    this.alreadyAssertedTypes = true;
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
            String.format("$%s_init", this.moduleName),
            ImmutableMap.of("$unused", TypeProvider.ImmediateTypeProvider.of(Types.STRING)),
            this.stmtListNode
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
                            key -> String.format("Injector.bindings.remove(new Key(\"%s\", %s));\n", key.name, key.type
                                .getJavaSourceClaroType()))
                        .collect(Collectors.joining()))
                    .append(
                        ModuleDefinitionStmt.this.optionalUsedModuleNameList.orElse(ImmutableList.of()).stream()
                            .map(depModuleName -> String.format("$%s_teardown.apply(\"unused\");\n", depModuleName))
                            .collect(Collectors.joining()))
                    .append("\n")
            );
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            ModuleDefinitionStmt.this.boundKeySet.forEach(key -> Injector.bindings.remove(key));
            ModuleDefinitionStmt.this.optionalUsedModuleNameList.orElse(ImmutableList.of()).forEach(
                depModuleName ->
                    ((Types.ProcedureType.ProcedureWrapper)
                         scopedHeap.getIdentifierValue(String.format("$%s_teardown", depModuleName)))
                        .apply(
                            ImmutableList.of(
                                new StringTerm("unused", null, -1, -1, -1)),
                            scopedHeap
                        ));
            return null;
          }
        })
    );
  }
}
