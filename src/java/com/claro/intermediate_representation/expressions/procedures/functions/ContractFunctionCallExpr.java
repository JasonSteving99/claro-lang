package com.claro.intermediate_representation.expressions.procedures.functions;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.Expr;
import com.claro.intermediate_representation.statements.contracts.ContractDefinitionStmt;
import com.claro.intermediate_representation.statements.contracts.ContractImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureSignatureDefinitionStmt;
import com.claro.intermediate_representation.types.*;
import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

public class ContractFunctionCallExpr extends FunctionCallExpr {
  private final String contractName;
  private final Optional<ImmutableList<TypeProvider>> optionalContractConcreteTypes;
  private String referencedContractImplName;
  private ImmutableList<Type> resolvedContractConcreteTypes;
  private Types.$Contract resolvedContractType;
  private String originalName;

  public ContractFunctionCallExpr(
      String contractName,
      ImmutableList<TypeProvider> contractConcreteTypes,
      String functionName,
      ImmutableList<Expr> args,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(contractName, Optional.of(contractConcreteTypes), functionName, args, currentLine, currentLineNumber, startCol, endCol);
  }

  public ContractFunctionCallExpr(
      String contractName,
      String functionName,
      ImmutableList<Expr> args,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    this(contractName, Optional.empty(), functionName, args, currentLine, currentLineNumber, startCol, endCol);
  }

  public ContractFunctionCallExpr(
      String contractName,
      Optional<ImmutableList<TypeProvider>> optionalContractConcreteTypes,
      String functionName,
      ImmutableList<Expr> args,
      Supplier<String> currentLine, int currentLineNumber, int startCol, int endCol) {
    super(
        // For now, we'll just masquerade as function name since we haven't resolved types yet. But we'll need to
        // canonicalize the name in a moment after type validation has gotten under way and types are known.
        functionName, args, currentLine, currentLineNumber, startCol, endCol);

    this.contractName = contractName;
    this.optionalContractConcreteTypes = optionalContractConcreteTypes;
  }

  @Override
  public void assertExpectedExprType(ScopedHeap scopedHeap, Type assertedOutputType) throws ClaroTypeException {
    resolveContractType(scopedHeap);

    // In the case that the Contract's concrete types were explicitly asserted, we have no extra work to do.
    // Otherwise, we have the extra task of inferring the Contract Implementation being referenced according
    // to the static types of the current arguments (and return type expected by the call-site's context).
    if (!this.optionalContractConcreteTypes.isPresent()) {
      ContractDefinitionStmt contractDefinitionStmt =
          (ContractDefinitionStmt) scopedHeap.getIdentifierValue(this.contractName);
      // The type param might come up multiple times in the signature for this procedure, so we need to ensure that
      // all usages of the type param have received arguments of the same type.
      ImmutableList<ContractProcedureSignatureDefinitionStmt.GenericSignatureType> genericSignatureArgTypes =
          contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name).resolvedArgTypes;
      ContractProcedureSignatureDefinitionStmt.GenericSignatureType outputGenericSignatureType =
          contractDefinitionStmt.declaredContractSignaturesByProcedureName.get(this.name).resolvedOutputType.get();
      ImmutableList.Builder<Type> contractConcreteTypesBuilder = ImmutableList.builder();
      for (String typeParamName : contractDefinitionStmt.typeParamNames) {
        Optional<Type> currTypeParamConcreteType = Optional.empty();
        for (int i = 0; i < genericSignatureArgTypes.size(); i++) {
          ContractProcedureSignatureDefinitionStmt.GenericSignatureType currGenericSignatureType =
              genericSignatureArgTypes.get(i);
          if (currGenericSignatureType.getOptionalResolvedType().isPresent()) {
            continue;
          }
          if (!currGenericSignatureType.getOptionalGenericTypeParamName().get().equals(typeParamName)) {
            continue;
          }

          // Careful with generic type params getting reused in a function signature.
          if (!currTypeParamConcreteType.isPresent()) {
            // This is the first arg of this generic type param, so we can just accept it as is.
            currTypeParamConcreteType = Optional.of(this.argExprs.get(i).getValidatedExprType(scopedHeap));
          } else {
            // This is not the first arg of this generic type param, so make sure it's equal to any prior matching type.
            this.argExprs.get(i).assertExpectedExprType(scopedHeap, currTypeParamConcreteType.get());
          }
        }
        // Apparently I was able infer the type of this arg, and validate that its usage matches in all necessary
        // locations its used in the arguments list. Now one last verification it matches with the return type if
        // applicable.
        if (currTypeParamConcreteType.isPresent()) {
          if (outputGenericSignatureType.getOptionalGenericTypeParamName().isPresent()
              && outputGenericSignatureType.getOptionalGenericTypeParamName().get().equals(typeParamName)) {
            if (!assertedOutputType.equals(currTypeParamConcreteType.get())) {
              throw new ClaroTypeException(currTypeParamConcreteType.get(), assertedOutputType);
            }
          }
        } else {
          // Apparently the current generic type param is not used as one of the arguments to this procedure. It's
          // type must be taken to be the type asserted by the surrounding context.
          currTypeParamConcreteType = Optional.of(assertedOutputType);
        }

        // I'm now confident that I've correctly inferred the type intended for this generic type param.
        contractConcreteTypesBuilder.add(currTypeParamConcreteType.get());
      }
      this.resolvedContractConcreteTypes = contractConcreteTypesBuilder.build();
    }

    super.assertExpectedExprType(scopedHeap, assertedOutputType);
  }

  @Override
  public Type getValidatedExprType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // We're handling both the case that this was called with a type statically asserted by the surrounding
    // context, and that it wasn't, so cleanup in the case that contract wasn't already looked up yet.
    if (this.resolvedContractType == null) {
      resolveContractType(scopedHeap);
    }
    if (this.optionalContractConcreteTypes.isPresent()) {
      this.resolvedContractConcreteTypes = this.optionalContractConcreteTypes.get().stream()
          .map(typeProvider -> typeProvider.resolveType(scopedHeap))
          .collect(ImmutableList.toImmutableList());
    } else if (this.resolvedContractConcreteTypes == null) {
      // TODO(steving) There's going to be a case where the program is ambiguous b/c the return type isn't asserted.
      throw new RuntimeException("TODO(steving) Need to handle inference when the type is not asserted by context." +
                                 " Sometimes it's actually valid.");
    }

    // Set the canonical implementation name so that this can be referenced later simply by type.
    ImmutableList<String> concreteTypeStrings =
        this.resolvedContractConcreteTypes.stream()
            .map(Type::toString)
            .collect(ImmutableList.toImmutableList());

    // Check that there are the correct number of type params.
    if (this.resolvedContractType.getTypeParamNames().size() != this.resolvedContractConcreteTypes.size()) {
      throw ClaroTypeException.forContractReferenceWithWrongNumberOfTypeParams(
          ContractImplementationStmt.getContractTypeString(this.contractName, concreteTypeStrings),
          ContractImplementationStmt.getContractTypeString(this.contractName, this.resolvedContractType.getTypeParamNames())
      );
    }

    // It's actually possible that this contract procedure is being called with all generic argument types. This is only
    // possible when type validation is being performed for a generic function when arg types are actually not known
    // yet. We'll just skip looking up the contract procedure in the scoped heap at that point and return the generic
    // type that would have aligned with the output type.
    this.originalName = this.name;
    boolean revertNameAfterTypeValidation = false;
    if (resolvedContractConcreteTypes.stream()
        .anyMatch(concreteContractType -> concreteContractType.baseType().equals(BaseType.$GENERIC_TYPE_PARAM))) {
      // Here, we're just doing a sanity check of a generic function definition, since we're checking against generic
      // type params rather than concrete types, so, just want to do a lookup on the non-impl contract procedure name in
      // the symbol table rather than a "real" implementation.
      this.name = String.format("$%s::%s", this.contractName, this.name);
      // We actually won't perform codegen on this generic type, so we need to use this mechanism to reset the name.
      revertNameAfterTypeValidation = true;
    } else {
      // We can now resolve the contract's concrete types so that we can canonicalize the function call name.
      this.name = ContractProcedureImplementationStmt.getCanonicalProcedureName(
          this.contractName,
          this.resolvedContractConcreteTypes,
          this.name
      );

      // Before leaving, just hold onto the corresponding contract's implementation name for codegen.
      this.referencedContractImplName =
          (String) scopedHeap.getIdentifierValue(
              ContractImplementationStmt.getContractTypeString(this.contractName, concreteTypeStrings));
    }

    // This final step defers validation of the actual types passed as args.
    Type res = super.getValidatedExprType(scopedHeap);
    if (revertNameAfterTypeValidation) {
      this.name = this.originalName;
    }
    return res;
  }

  private void resolveContractType(ScopedHeap scopedHeap) throws ClaroTypeException {
    // Validate that the contract name is valid.
    if (!scopedHeap.isIdentifierDeclared(this.contractName)) {
      throw ClaroTypeException.forReferencingUnknownContract(this.contractName);
    }
    Type contractType = scopedHeap.getValidatedIdentifierType(this.contractName);
    if (!contractType.baseType().equals(BaseType.$CONTRACT)) {
      throw new ClaroTypeException(scopedHeap.getValidatedIdentifierType(this.contractName), BaseType.$CONTRACT);
    }
    this.resolvedContractType = (Types.$Contract) contractType;

    // Validate that the referenced procedure name is even actually in the referenced contract.
    if (!resolvedContractType.getProcedureNames().contains(this.originalName == null ? this.name : this.originalName)) {
      throw ClaroTypeException.forContractReferenceUndefinedProcedure(
          this.contractName, this.resolvedContractType.getTypeParamNames(), this.name);
    }
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res =
        GeneratedJavaSource.forJavaSourceBody(
            new StringBuilder(this.referencedContractImplName).append('.'));
    res = res.createMerged(super.generateJavaSourceOutput(scopedHeap));

    // This node will be potentially reused assuming that it is called within a Generic function that gets
    // monomorphized as that process will reuse the exact same nodes over multiple sets of types. So reset
    // the name now.
    this.name = this.originalName;

    return res;
  }
}
