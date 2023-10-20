package com.claro.intermediate_representation.expressions.term;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.expressions.procedures.functions.FunctionCallExpr;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;

import java.util.Optional;
import java.util.function.Supplier;

public class IdentifierReferenceTerm extends Term {

  public final String identifier;
  private final Optional<String> optionalDefiningModuleDisambiguator;
  private Optional<Supplier<String>> alternateCodegenString = Optional.empty();
  private boolean contextualTypeAsserted = false;

  public IdentifierReferenceTerm(String identifier, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(identifier, Optional.empty(), currentLine, currentLineNumber, startCol, endCol);
  }

  public IdentifierReferenceTerm(String identifier, Optional<String> optionalDefiningModuleDisambiguator, Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(currentLine, currentLineNumber, startCol, endCol);
    // Hold onto the relevant data for code-gen later.
    this.identifier = identifier;
    this.optionalDefiningModuleDisambiguator = optionalDefiningModuleDisambiguator;
  }

  public IdentifierReferenceTerm withIdentifier(String identifier) {
    return new IdentifierReferenceTerm(identifier, this.optionalDefiningModuleDisambiguator, super.currentLine, super.currentLineNumber, super.startCol, super.endCol);
  }

  public String getIdentifier() {
    return identifier;
  }

  // We would like to support treating certain referenced identifiers as the asserted type, even if the Expr
  // referenced by this identifier isn't strictly the same type. This will, in particular, allow blocking-generic
  // procedures to be assigned to non-blocking-generic variables as higher order procedures by erasing the
  // blocking-generic type annotation.
  @Override
  public boolean coerceExprToExpectedType(Type expectedExprType, Type actualExprType, ScopedHeap scopedHeap) {
    // Don't bother doing any coercion if the base types don't at least match.
    if (!actualExprType.baseType().equals(expectedExprType.baseType()) ||
        actualExprType.baseType().equals(BaseType.ONEOF)) {
      // Defer back to the basic implementation which is going to handle the case of trying to do structural inference
      // to allow for the possibility that a oneof may match a concrete variant type.
      return super.coerceExprToExpectedType(expectedExprType, actualExprType, scopedHeap);
    }

    ImmutableSet<BaseType> allowedBaseTypesForBlockingGenerics =
        ImmutableSet.of(BaseType.FUNCTION, BaseType.CONSUMER_FUNCTION);

    // It's possible to coerce a Generic Procedure into a concrete first class procedure.
    if (actualExprType instanceof Types.ProcedureType
        && ((Types.ProcedureType) actualExprType).getGenericProcedureArgNames().isPresent()
        // Conceptually, we could allow narrowing generic types while staying generic, but
        // I don't think this is worth the added complexity at least w/o hearing some user demand.
        && !((Types.ProcedureType) expectedExprType).getGenericProcedureArgNames().isPresent()) {
      if (!(actualExprType instanceof Types.ProcedureType.FunctionType)) {
        // TODO(steving) Get rid of this limitation once generics are supported on all sorts of procedures.
        throw new RuntimeException("Internal Compiler Error: TODO(steving) Claro currently doesn't support using Generic Consumers or Providers as first class objects.");
      }
      // A simple way to check that this type coercion will work is by creating a synthetic FunctionCallExpr over
      // args of the asserted type(s) and see if the function call would be accepted! Simple and elegant :).
      FunctionCallExpr syntheticFunctionCallForTypeValidation =
          new FunctionCallExpr(
              this.identifier,
              ((Types.ProcedureType) expectedExprType).getArgTypes().stream()
                  .map(expectedArgType ->
                           // Just plumbing our expected types into the FunctionCallExpr.
                           Term.getDummyTerm(expectedArgType, "unused"))
                  .collect(ImmutableList.toImmutableList()),
              this.currentLine, this.currentLineNumber, this.startCol, this.endCol
          );
      int numTypeErrorsLoggedBefore = Expr.typeErrorsFound.size();
      try {
        syntheticFunctionCallForTypeValidation.assertExpectedExprType(scopedHeap, ((Types.ProcedureType) expectedExprType)
            .getReturnType());
      } catch (Exception e) {
        return false;
      }
      if (Expr.typeErrorsFound.size() > numTypeErrorsLoggedBefore) {
        return false;
      }

      // We were successfully able to validate that this is a valid type coercion! Let's make sure we get the updated
      // identifier to use for codegen.
      // In order to call the actual monomorphization, we need to ensure that the name isn't too long for Java.
      // So, we're following a hack where all monomorphization names are sha256 hashed to keep them short while
      // still unique.
      this.alternateCodegenString =
          Optional.of(
              () -> String.format(
                  "%s__%s",
                  this.identifier,
                  Hashing.sha256().hashUnencodedChars(syntheticFunctionCallForTypeValidation.name).toString()
              )
          );
      return true;
    }
    // We're possibly able to coerce blocking-generic procedures into their non-blocking-generic equivalent.
    else if (allowedBaseTypesForBlockingGenerics.contains(actualExprType.baseType())
             && ((Types.ProcedureType) actualExprType).getAnnotatedBlockingGenericOverArgs().isPresent()
             // Conceptually, we could allow narrowing blocking-generic args, while staying blocking-generic,
             // but, I don't think this is worth the added complexity at least w/o hearing some user demand.
             && !((Types.ProcedureType) expectedExprType).getAnnotatedBlockingGenericOverArgs().isPresent()) {
      Types.ProcedureType actualProcedureType = (Types.ProcedureType) actualExprType;
      Types.ProcedureType expectedProcedureType = (Types.ProcedureType) expectedExprType;

      Type legalTypeCoercion;
      // For now, we only give the user two options for coercion:
      // blocking-generic -> all-args-blocking
      if (expectedProcedureType.getAnnotatedBlocking()) {
        legalTypeCoercion =
            scopedHeap.getValidatedIdentifierType(
                "$blockingConcreteVariant_"
                // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather than
                // just the name of the identifier since procedures may be assigned to vars as first class objects.
                + (((Types.ProcedureType) actualExprType).getProcedureDefStmt())
                    .opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets.get());
      } else {
        // blocking-generic -> non-blocking
        legalTypeCoercion =
            scopedHeap.getValidatedIdentifierType(
                "$nonBlockingConcreteVariant_"
                // Refer to the name of the procedure as defined by the owning ProcedureDefinitionStmt rather than
                // just the name of the identifier since procedures may be assigned to vars as first class objects.
                + (((Types.ProcedureType) actualExprType).getProcedureDefStmt())
                    .opaqueData_workaroundToAvoidCircularDepsCausedByExprToStmtBuildTargets.get());
      }

      return legalTypeCoercion.equals(expectedExprType);
    }

    // False, indicates that coercion was not possible for the given identifier and expected type due to basic type
    // mismatch error. We won't throw exceptions here because superclass will log this basic type mismatch for us.
    return false;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type expectedExprType) throws ClaroTypeException {
    // Just want to store whether or not the type's being manually asserted here.
    this.contextualTypeAsserted = true;
    super.assertExpectedExprType(scopedHeap, expectedExprType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Make sure we check this will actually be a valid reference before we allow it.
    try {
      Preconditions.checkState(
          scopedHeap.isIdentifierDeclared(this.identifier),
          "No variable <%s> within the current scope!",
          this.identifier
      );
      Preconditions.checkState(
          scopedHeap.isIdentifierInitialized(this.identifier),
          "Variable <%s> may not have been initialized!",
          this.identifier
      );
    } catch (IllegalStateException e) {
      this.logTypeError(e);
      return Types.UNKNOWABLE;
    }
    scopedHeap.markIdentifierUsed(this.identifier);
    Type referencedIdentifierType = scopedHeap.getValidatedIdentifierType(this.identifier);

    if (referencedIdentifierType.autoValueIgnored_IsNarrowedType.get()) {
      String narrowedTypeSyntheticIdentifier = String.format("$NARROWED_%s", this.identifier);
      if (scopedHeap.isIdentifierDeclared(narrowedTypeSyntheticIdentifier)) {
        referencedIdentifierType = scopedHeap.getValidatedIdentifierType(narrowedTypeSyntheticIdentifier);
        Type finalReferencedIdentifierType = referencedIdentifierType;
        this.alternateCodegenString =
            Optional.of(() -> String.format("((%s) %s)", finalReferencedIdentifierType.getJavaSourceType(), this.identifier));
      }
    }

    // Unfortunately, unlike with blocking-generic-only procedures, we know ahead of time that we cannot allow
    // references to Generic Procedures as first class objects unless a matching concrete type signature is
    // asserted in the source: e.g.
    // For signature: function genericFooFn<T,V>(t: T) -> V {...}
    // Good: `var fn: function<int -> string> = genericFooFn;`
    // Bad:  `var fn = genericFooFn;`
    // This is because Claro Generics are implemented via monomorphization and so the procedure bodies themselves
    // that actually execute the calls, are not the same for all concrete type specializations so the function being
    // called must be known at compile-time, and can't be decided at runtime. It's possible that I'm missing some
    // simple insight that would make this possible, but it's not important enough for me to do a lot of thinking
    // and work to enable this minimally important behavior.
    if (!this.contextualTypeAsserted
        && referencedIdentifierType instanceof Types.ProcedureType
        && ((Types.ProcedureType) referencedIdentifierType).getGenericProcedureArgNames().isPresent()) {
      throw ClaroTypeException.forInvalidGenericProcedureReferenceAsFirstClassObjectWithoutContextualTypeAssertion(
          this.identifier, referencedIdentifierType);
    }

    // Now that everything is validated, check if this ref is inside a nested comprehension expr, and handle it.
    if (InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionCollectionsCount >= 0) {
      InternalStaticStateUtil.addNestedCollectionIdentifierReference(this.identifier);
    }

    // If this identifier happens to be a lambda capture, then its type must be deeply-immutable otherwise the reference
    // is actually illegal.
    if (scopedHeap.scopeStack.get(scopedHeap.findIdentifierInitializedScopeLevel(this.identifier).get())
            .lambdaScopeCapturedVariables.containsKey(this.identifier)
        && !Types.isDeeplyImmutable(referencedIdentifierType)) {
      this.logTypeError(ClaroTypeException.forIllegalLambdaCaptureOfMutableType(referencedIdentifierType));
    }

    return referencedIdentifierType;
  }

  @Override
  public StringBuilder generateJavaSourceBodyOutput(ScopedHeap scopedHeap) {
    ScopedHeap.IdentifierData identifierData = scopedHeap.getIdentifierData(this.identifier);
    identifierData.used = true;
    return new StringBuilder(
        this.alternateCodegenString.orElse(
            () -> {
              if (identifierData.isStaticValue) {
                if (identifierData.type.baseType().equals(BaseType.USER_DEFINED_TYPE) &&
                    ((Types.UserDefinedType) identifierData.type).getDefiningModuleDisambiguator()
                        .equals("src$java$com$claro$stdlib$claro$files$files")) {
                  // If we're dealing with a synthetic resource reference, then instead of codegening a reference to
                  // some pre-existing identifier, I need to codegen the resource lookup.
                  String resourceJarLocation = (String) identifierData.interpretedValue;
                  // Bazel determines resource file location based on project structure, so this canonicalizes that.
                  //     See: https://bazel.build/reference/be/java#java_binary_args
                  if (resourceJarLocation.contains("/java/")) {
                    resourceJarLocation =
                        resourceJarLocation.substring(resourceJarLocation.lastIndexOf("/java/") + "/java/".length());
                  } else if (resourceJarLocation.contains("/src/")) {
                    resourceJarLocation =
                        resourceJarLocation.substring(resourceJarLocation.lastIndexOf("/src/") + "/src/".length());
                  }
                  return String.format(
                      "new $UserDefinedType(\"Resource\", \"src$java$com$claro$stdlib$claro$files$files\", ImmutableList.of(), %s, %s.class.getResource(\"/%s\"))",
                      Types.RESOURCE_URL.getJavaSourceClaroType(),
                      InternalStaticStateUtil.optionalClaroBinaryGeneratedClassName
                          .orElseGet(this::getFullySpecifiedIdentifierNamespace),
                      resourceJarLocation
                  );
                }
                // To ensure that static values can be referenced across dep module monomorphization boundaries, I need
                // to fully specify their namespace at all times.
                String codegenIdentifier = this.optionalDefiningModuleDisambiguator
                    .map(unused -> {
                      int identifierEndIndex = this.identifier.indexOf('$');
                      if (identifierEndIndex == -1) {
                        return this.identifier;
                      }
                      return this.identifier.substring(0, identifierEndIndex);
                    })
                    .orElse(this.identifier);
                if (identifierData.isLazyValue) {
                  return String.format(
                      "%slazyStaticInitializer$%s()",
                      getFullySpecifiedIdentifierNamespace(),
                      codegenIdentifier
                  );
                }
                return getFullySpecifiedIdentifierNamespace() + codegenIdentifier;
              } else if (identifierData.type.baseType().equals(BaseType.ATOM) && identifierData.isTypeDefinition) {
                // Here it turns out that we actually need to codegen a lookup into the ATOM CACHE.
                return String.format(
                    "%sATOM_CACHE[%s]",
                    getFullySpecifiedIdentifierNamespace(),
                    InternalStaticStateUtil.AtomDefinition_CACHE_INDEX_BY_MODULE_AND_ATOM_NAME.build().get(
                        this.optionalDefiningModuleDisambiguator.orElseGet(
                            () -> ScopedHeap.getDefiningModuleDisambiguator(Optional.empty())),
                        this.identifier
                    )
                );
              } else if (InternalStaticStateUtil.ComprehensionExpr_nestedComprehensionIdentifierReferences.contains(this.identifier)) {
                // Nested comprehension Exprs depend on a synthetic class wrapping the nested identifier refs to
                // workaround Java's restriction that all lambda captures must be effectively final.
                return "$nestedComprehensionState." + this.identifier;
              }
              return this.identifier;
            }
        ).get());
  }

  // Returns empty only if referencing an identifier from the current compilation unit, and the current compilation unit
  // is in fact the top-level claro_binary(). Else, returns the actual UniqueModuleDescriptor to explicitly reference
  // currently identifier.
  private String getFullySpecifiedIdentifierNamespace() {
    return ScopedHeap.getModuleNameFromDisambiguator(
            this.optionalDefiningModuleDisambiguator.orElse("$THIS_MODULE$"))
        .map(moduleName ->
                 ScopedHeap.currProgramDepModules.rowMap().get(moduleName)
                     .values().stream().findFirst().get())
        .map(m -> String.format("%s.%s.", m.getProjectPackage(), m.getUniqueModuleName()))
        .orElse("");
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    scopedHeap.markIdentifierUsed(this.identifier);
    return scopedHeap.getIdentifierValue(this.identifier);
  }
}
