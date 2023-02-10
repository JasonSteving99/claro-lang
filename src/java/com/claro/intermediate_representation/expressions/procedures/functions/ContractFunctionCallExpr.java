package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ContractFunctionCallExpr extends FunctionCallExpr {
  private final String contractName;
  private String referencedContractImplName;
  private ImmutableList<Type> resolvedContractConcreteTypes;
  private Types.$Contract resolvedContractType;
  private String originalName;
  private Type assertedOutputType;

  public ContractFunctionCallExpr(
      String contractName,
      String functionName,
      ImmutableList<Expr> args,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(
        // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
        // canonicalize the name in a moment after type validation has gotten under way and types are known.
        functionName, args, currentLine, currentLineNumber, startCol, endCol);

    this.contractName = contractName;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type assertedOutputType) throws ClaroTypeException {
    this.resolveContractType(scopedHeap);

    // Before we do any type checking, we essentially want to disable worrying about exact matches on generic
    // type params. For a contract, we simply would want to know that the contract is listed in the requirements
    // but for its generic argument positions it can be passed literally any type (since the existence of that
    // contract impl will actually be validated at the callsite to the generic function, not here).
    Expr.validatingContractProcCallWithinGenericProc = true;

    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name);

    // Infer all types starting from the return type as necessary.
    HashMap<Type, Type> inferredConcreteTypes = Maps.newHashMap();
    // Let FunctionCallExpr that we're about to defer to handle throwing the exception if this was called over some
    // consumer that doesn't even have an output type.
    if (contractProcedureSignatureDefinitionStmt.resolvedOutputType.isPresent()) {
      // Need to infer the output type first to preserve the semantic that the contextual types provide better type
      // checking on the args necessary to get that desired output type.
      try {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            inferredConcreteTypes,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType(),
            assertedOutputType
        );
      } catch (ClaroTypeException ignored) {
        // We want cleaner error messages that indicate the type mismatch more clearly.
        throw new ClaroTypeException(
            assertedOutputType,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
        );
      }
      // Now, using the type information gained from the output type check, check all of the args.
      for (Integer i : contractProcedureSignatureDefinitionStmt.inferContractImplTypesFromArgsWhenContextualOutputTypeAsserted.get()) {
        try {
          StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
              inferredConcreteTypes,
              contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i).toType(),
              this.argExprs.get(i).getValidatedExprType(scopedHeap)
          );
        } catch (ClaroTypeException ignored) {
          // We want cleaner error messages that indicate the type mismatch more clearly.
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.of(inferredConcreteTypes);
          this.argExprs.get(i)
              .assertExpectedExprType(scopedHeap, contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i)
                  .toType());
          Types.$GenericTypeParam.concreteTypeMappingsForBetterErrorMessages = Optional.empty();
        }
      }
      this.resolvedContractConcreteTypes = contractDefinitionStmt.typeParamNames.stream()
          .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
          .collect(ImmutableList.toImmutableList());
    }

    this.assertedOutputType = assertedOutputType;
    super.assertExpectedExprType(scopedHeap, assertedOutputType);

    // Return type validation to the default state where Generic type params must be strictly checked for equality.
    Expr.validatingContractProcCallWithinGenericProc = false;
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // We're handling both the case that this was called with a type statically asserted by the surrounding
    // context, and that it wasn't, so cleanup in the case that contract wasn't already looked up yet.
    if (this.resolvedContractType == null) {
      this.resolveContractType(scopedHeap);
    }

    ContractDefinitionStmt contractDefinitionStmt =
        (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
    ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt =
        contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name);
    if (this.assertedOutputType == null && contractProcedureSignatureDefinitionStmt.resolvedOutputType.isPresent()) {
      if (contractProcedureSignatureDefinitionStmt.contextualOutputTypeAssertionRequired) {
        // In this case the program is ambiguous when the return type isn't asserted. The output type of this particular
        // Contract procedure call happens to be composed around a Contract type param that is NOT present in one of the
        // other args, therefore making Contract Impl inference via the arg types alone impossible.
        throw ClaroTypeException.forContractProcedureCallWithoutRequiredContextualOutputTypeAssertion(
            this.contractName,
            contractDefinitionStmt.typeParamNames,
            this.name,
            contractProcedureSignatureDefinitionStmt.resolvedOutputType.get().toType()
        );
      }
    }

    Type res = this.getValidatedTypeInternal(
        contractDefinitionStmt, contractProcedureSignatureDefinitionStmt, scopedHeap);

    // In case we actually would return a generic type here (bc we're still within a generic type validation phase), we
    // can just go ahead and assume it safe to return the asserted type. This is because we know that when the Generic
    // Procedure that this contract function call is made within is actually called, the correct monomorphization for
    // this contract will be selected since the generic procedure's contract requirements will have been validated.
    if (res.baseType().equals(BaseType.$GENERIC_TYPE_PARAM)) {
      return this.assertedOutputType;
    }
    return res;
  }

  private Type getValidatedTypeInternal(
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.name);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    AtomicReference<String> referencedContractImplName_OUT_PARAM =
        new AtomicReference<>(this.referencedContractImplName);
    AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractConcreteTypes);
    boolean revertNameAfterTypeValidation;
    try {
      revertNameAfterTypeValidation =
          getValidatedTypeInternal(
              this.contractName,
              this.resolvedContractType,
              contractDefinitionStmt,
              contractProcedureSignatureDefinitionStmt,
              this.argExprs,
              Optional.of(this::logTypeError),
              scopedHeap,
              resolvedContractConcreteTypes_OUT_PARAM,
              procedureName_OUT_PARAM,
              originalName_OUT_PARAM,
              referencedContractImplName_OUT_PARAM
          );
    } finally {
      this.name = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
      this.referencedContractImplName = referencedContractImplName_OUT_PARAM.get();
      this.resolvedContractConcreteTypes = resolvedContractConcreteTypes_OUT_PARAM.get();
    }
    // This final step defers validation of the actual types passed as args.
    Type res = super.getValidatedExprType(scopedHeap);
    if (revertNameAfterTypeValidation) {
      this.name = originalName_OUT_PARAM.get();
    }
    return res;
  }


  public static boolean getValidatedTypeInternal(
      final String contractName,
      final Types.$Contract resolvedContractType,
      ContractDefinitionStmt contractDefinitionStmt,
      ContractProcedureSignatureDefinitionStmt contractProcedureSignatureDefinitionStmt,
      ImmutableList<Expr> argExprs,
      final Optional<Consumer<Exception>> optionalLogTypeError,
      final ScopedHeap scopedHeap,
      AtomicReference<ImmutableList<Type>> resolvedContractConcreteTypes_OUT_PARAM,
      AtomicReference<String> procedureName_OUT_PARAM,
      AtomicReference<String> originalName_OUT_PARAM,
      AtomicReference<String> referencedContractImplName_OUT_PARAM) throws ClaroTypeException {
    // Do type inference of the concrete types by checking only the necessary args.
    if (resolvedContractConcreteTypes_OUT_PARAM.get() == null) {
      // It's actually possible to fully infer the Contract Type Params based strictly off arg type inference.
      HashMap<Type, Type> inferredConcreteTypes = Maps.newHashMap();
      for (Integer i : contractProcedureSignatureDefinitionStmt.inferContractImplTypesFromArgs) {
        StructuralConcreteGenericTypeValidationUtil.validateArgExprsAndExtractConcreteGenericTypeParams(
            inferredConcreteTypes,
            contractProcedureSignatureDefinitionStmt.resolvedArgTypes.get(i).toType(),
            argExprs.get(i).getValidatedExprType(scopedHeap)
        );
      }
      resolvedContractConcreteTypes_OUT_PARAM.set(
          contractDefinitionStmt.typeParamNames.stream()
              .map(n -> inferredConcreteTypes.get(Types.$GenericTypeParam.forTypeParamName(n)))
              .collect(ImmutableList.toImmutableList()));
    }

    // If this contract procedure is getting called over any generic type params, then we need to validate that the
    // generic function it's getting called within actually already marks this particular contract impl as `required`.
    CheckContractImplAnnotatedRequiredWithinGenericFunctionDefinition:
    {
      if (resolvedContractConcreteTypes_OUT_PARAM.get().stream()
          .anyMatch(contractTypeParam -> contractTypeParam.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))) {
        Optional optionalRequiredContractNamesToGenericArgs =
            Optional.ofNullable(
                ((Types.ProcedureType) InternalStaticStateUtil.ProcedureDefinitionStmt_optionalActiveProcedureResolvedType
                    .get())
                    .getAllTransitivelyRequiredContractNamesToGenericArgs());
        if (!optionalRequiredContractNamesToGenericArgs.isPresent()) {
          // In the case that we're within a lambda expr defined w/in a generic procedure definition, we need to grab
          // the required contract impls info from a different source.
          optionalRequiredContractNamesToGenericArgs =
              InternalStaticStateUtil.LambdaExpr_optionalActiveGenericProcedureDefRequiredContractNamesToGenericArgs;
        }
        if (optionalRequiredContractNamesToGenericArgs.isPresent()
            && !((ListMultimap<String, ImmutableList<Types.$GenericTypeParam>>)
                     optionalRequiredContractNamesToGenericArgs.get()).isEmpty()) {
          // There are actually some contracts annotated required, let's look for one that would match the current call.
          for (ImmutableList<Types.$GenericTypeParam> annotatedRequiredContractImplTypes :
              ((ListMultimap<String, ImmutableList<Types.$GenericTypeParam>>)
                   optionalRequiredContractNamesToGenericArgs.get()).get(contractName)) {
            if (annotatedRequiredContractImplTypes.equals(resolvedContractConcreteTypes_OUT_PARAM.get())) {
              // Good job programmer!
              break CheckContractImplAnnotatedRequiredWithinGenericFunctionDefinition;
            }
          }
        }
        // Let's not make this a terminal exception that prevents continuing type checking. Just mark the error and
        // continue on with type checking to find more errors.
        ClaroTypeException badContractReferenceWithoutRequiresAnnotation =
            ClaroTypeException.forContractProcedureReferencedWithoutRequiredAnnotationOnGenericFunction(
                contractName, procedureName_OUT_PARAM.get(), resolvedContractConcreteTypes_OUT_PARAM.get());
        if (optionalLogTypeError.isPresent()) {
          optionalLogTypeError.get()
              .accept(badContractReferenceWithoutRequiresAnnotation);
        } else {
          throw badContractReferenceWithoutRequiresAnnotation;
        }
      }
    }

    // Set the canonical implementation name so that this can be referenced later simply by type.
    ImmutableList<String> concreteTypeStrings =
        resolvedContractConcreteTypes_OUT_PARAM.get().stream()
            .map(Type::toString)
            .collect(ImmutableList.toImmutableList());

    // Check that there are the correct number of type params.
    if (resolvedContractType.getTypeParamNames().size() != resolvedContractConcreteTypes_OUT_PARAM.get().size()) {
      throw ClaroTypeException.forContractReferenceWithWrongNumberOfTypeParams(
          ContractImplementationStmt.getContractTypeString(contractName, concreteTypeStrings),
          ContractImplementationStmt.getContractTypeString(contractName, resolvedContractType.getTypeParamNames())
      );
    }

    // It's actually possible that this contract procedure is being called with all generic argument types. This is only
    // possible when type validation is being performed for a generic function when arg types are actually not known
    // yet. We'll just skip looking up the contract procedure in the scoped heap at that point and return the generic
    // type that would have aligned with the output type.
    originalName_OUT_PARAM.set(procedureName_OUT_PARAM.get());
    boolean revertNameAfterTypeValidation =
        InternalStaticStateUtil.GnericProcedureDefinitionStmt_withinGenericProcedureDefinitionTypeValidation;
    if (resolvedContractConcreteTypes_OUT_PARAM.get().stream()
        .anyMatch(concreteContractType -> concreteContractType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))) {
      // Here, we're just doing a sanity check of a generic function definition, since we're checking against generic
      // type params rather than concrete types, so, just want to do a lookup on the non-impl contract procedure name in
      // the symbol table rather than a "real" implementation.
      procedureName_OUT_PARAM.set(String.format("$%s::%s", contractName, procedureName_OUT_PARAM.get()));
    } else {
      // We can now resolve the contract's concrete types so that we can canonicalize the function call name.
      procedureName_OUT_PARAM.set(
          ContractProcedureImplementationStmt.getCanonicalProcedureName(
              contractName,
              resolvedContractConcreteTypes_OUT_PARAM.get(),
              procedureName_OUT_PARAM.get()
          )
      );

      String contractImplTypeString =
          ContractImplementationStmt.getContractTypeString(contractName, concreteTypeStrings);
      if (!scopedHeap.isIdentifierDeclared(contractImplTypeString)) {
        throw ClaroTypeException.forContractProcedureCallOverUnimplementedConcreteTypes(contractImplTypeString);
      } else {
        // Before leaving, just hold onto the corresponding contract's implementation name for codegen.
        referencedContractImplName_OUT_PARAM.set(
            (String) scopedHeap.getIdentifierValue(contractImplTypeString));
      }
    }
    return revertNameAfterTypeValidation;
  }

  private void resolveContractType(ScopedHeap scopedHeap) throws ClaroTypeException {
    AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM =
        new AtomicReference<>(this.resolvedContractType);
    AtomicReference<String> procedureName_OUT_PARAM = new AtomicReference<>(this.name);
    AtomicReference<String> originalName_OUT_PARAM = new AtomicReference<>(this.originalName);
    try {
      ContractFunctionCallExpr.resolveContractType(
          this.contractName, scopedHeap, resolvedContractType_OUT_PARAM, procedureName_OUT_PARAM, originalName_OUT_PARAM);
    } finally {
      // To simulate "out params" no matter what happens in this call, the side effects must occur.
      this.resolvedContractType = resolvedContractType_OUT_PARAM.get();
      this.name = procedureName_OUT_PARAM.get();
      this.originalName = originalName_OUT_PARAM.get();
    }
  }

  // In order to get some code reuse across ContractFunctionCallExpr and ContractConsumerCallStmt I unfortunately need
  // to enable side effects to propagate to both callers. This hacked "out params" design is simply because Java doesn't
  // give me any language level mechanism for this single procedure to have the desired side effect on both types...
  // Claro will fix this by providing monomorphized generics over fields.
  public static void resolveContractType(
      String contractName,
      ScopedHeap scopedHeap,
      // Java is garbage so there's no painless way to do anything resembling multiple returns, so instead here's this
      // gnarly hack. Taking in atomic refs so that I can simply use these as "out params" for lack of a better
      // mechanism w/ language support....
      AtomicReference<Types.$Contract> resolvedContractType_OUT_PARAM,
      AtomicReference<String> procedureName_OUT_PARAM,
      AtomicReference<String> originalName_OUT_PARAM)
      throws ClaroTypeException {
    // Validate that the contract name is valid.
    if (!scopedHeap.isIdentifierDeclared(contractName)) {
      throw ClaroTypeException.forReferencingUnknownContract(contractName);
    }
    Type contractType = scopedHeap.getValidatedIdentifierType(contractName);
    if (!contractType.baseType().equals(BaseType.$CONTRACT)) {
      throw new ClaroTypeException(scopedHeap.getValidatedIdentifierType(contractName), BaseType.$CONTRACT);
    }
    resolvedContractType_OUT_PARAM.set((Types.$Contract) contractType);

    // Validate that the referenced procedure name is even actually in the referenced contract.
    if (!resolvedContractType_OUT_PARAM.get().getProcedureNames().contains(
        originalName_OUT_PARAM.get() == null ? procedureName_OUT_PARAM.get() : originalName_OUT_PARAM.get())) {
      throw ClaroTypeException.forContractReferenceUndefinedProcedure(
          contractName, resolvedContractType_OUT_PARAM.get().getTypeParamNames(), procedureName_OUT_PARAM.get());
    }

    // Ensure that we're starting off with a clean slate in regards to this.name being set to the original procedure
    // name. This is in the case that this contract function call is actually happening within a blocking-generic
    // procedure body, in which case the same ContractFunctionCallExpr node is being reused for type checking.
    if (originalName_OUT_PARAM.get() != null) {
      procedureName_OUT_PARAM.set(originalName_OUT_PARAM.get());
      originalName_OUT_PARAM.set(null);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(this.referencedContractImplName).append('.'));

    // In order to avoid using names that are way too long for Java, we're going to hash all names within this
    // contract implementation. I won't worry about maintaining the old names here, because these variables should
    // never be referenced anymore after codegen.
    super.hashNameForCodegen = true;
    res = res.createMerged(super.generateJavaSourceOutput(scopedHeap));

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.name = this.originalName;

    return res;
  }
}
