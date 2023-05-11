package com.claro.intermediate_representation.statements;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.expressions.term.IdentifierReferenceTerm;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.internal_static_state.InternalStaticStateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.HashMap;

public class EndpointHandlersBlockStmt extends Stmt {
  private final IdentifierReferenceTerm serviceName;
  private final ImmutableList<GraphProcedureDefinitionStmt> endpointImpls;

  public EndpointHandlersBlockStmt(
      IdentifierReferenceTerm serviceName, ImmutableList<GraphProcedureDefinitionStmt> endpointImpls) {
    super(ImmutableList.of());
    this.serviceName = serviceName;
    this.endpointImpls = endpointImpls;
  }

  public void registerEndpointHandlerProcedureTypeProviders(ScopedHeap scopedHeap) {
    for (GraphProcedureDefinitionStmt endpointImpl : this.endpointImpls) {
      endpointImpl.procedureName = endpointImpl.procedureName + "$EndpointHandler";
      endpointImpl.registerProcedureTypeProvider(scopedHeap);
    }
  }

  @Override
  public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
    boolean canValidateHandlerSignatureTypes = true;
    if (!scopedHeap.isIdentifierDeclared(this.serviceName.identifier)) {
      this.serviceName.logTypeError(ClaroTypeException.forInvalidEndpointHandlersBlockForHttpServiceUndefined());
      canValidateHandlerSignatureTypes = false;
    }

    Type referencedServiceType = scopedHeap.getValidatedIdentifierType(this.serviceName.identifier);
    if (!referencedServiceType.baseType().equals(BaseType.HTTP_SERVICE)) {
      this.serviceName.logTypeError(
          ClaroTypeException.forInvalidEndpointHandlersBlockForNonHttpService(this.serviceName.identifier, referencedServiceType));
      canValidateHandlerSignatureTypes = false;
    }

    HashMap<String, ImmutableList<Type>> invalidEndpointSignatures = Maps.newHashMap();
    for (GraphProcedureDefinitionStmt endpointImpl : endpointImpls) {
      String endpointName = endpointImpl.procedureName.substring(0, endpointImpl.procedureName.indexOf("$"));
      Type expectedEndpointHandlerType =
          InternalStaticStateUtil.HttpServiceDef_endpointProcedureSignatures.get(
              this.serviceName.identifier, endpointName);

      endpointImpl.assertExpectedExprTypes(scopedHeap);

      if (canValidateHandlerSignatureTypes && !endpointImpl.resolvedProcedureType.equals(expectedEndpointHandlerType)) {
        invalidEndpointSignatures.put(
            endpointName,
            ImmutableList.of(endpointImpl.resolvedProcedureType, expectedEndpointHandlerType)
        );
      }
    }

    if (!invalidEndpointSignatures.isEmpty()) {
      throw ClaroTypeException.forInvalidEndpointHandlersNotSatisfyingRequiredSignatureForHandledHttpService(
          this.serviceName.identifier,
          invalidEndpointSignatures
      );
    }

    // Make note of this endpoint_handlers block being successfully validated.
    InternalStaticStateUtil.HttpServiceDef_servicesWithValidEndpointHandlersDefined.add(this.serviceName.identifier);
  }

  @Override
  public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
    GeneratedJavaSource res = GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
    for (GraphProcedureDefinitionStmt endpointImpl : this.endpointImpls) {
      res = res.createMerged(endpointImpl.generateJavaSourceOutput(scopedHeap));
    }
    return res;
  }

  @Override
  public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
    // TODO(steving) Eventually need to impl endpoint_handlers when I come back to adding support for the interpreted backend.
    throw new RuntimeException("Internal Compiler Error! Claro doesn't support `endpoint_handlers` in the interpreted backend just yet!");
  }
}
