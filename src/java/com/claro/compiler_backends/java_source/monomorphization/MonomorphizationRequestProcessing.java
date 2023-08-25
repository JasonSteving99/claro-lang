package com.claro.compiler_backends.java_source.monomorphization;

import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;

public class MonomorphizationRequestProcessing {

  public static String handleMonomorphizationRequest(String base64EncodedMonomorphizationRequest) {
    MonomorphizationRequest monomorphizationRequest;
    try {
      monomorphizationRequest = MonomorphizationRequest.parseFrom(
          BaseEncoding.base64().decode(base64EncodedMonomorphizationRequest));
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to parse MonomorphizationRequest!", e);
    }

    // TODO(steving) I need to do a lot more work here than just a simple name generation, but this is representative.
    return ContractProcedureImplementationStmt.getCanonicalProcedureName(
        "$MONOMORPHIZATION"/*contractName=*/,
        monomorphizationRequest.getConcreteTypeParamsList().stream()
            .map(Types::parseTypeProto)
            .collect(ImmutableList.toImmutableList()),
        monomorphizationRequest.getProcedureName()
    );
  }

}
