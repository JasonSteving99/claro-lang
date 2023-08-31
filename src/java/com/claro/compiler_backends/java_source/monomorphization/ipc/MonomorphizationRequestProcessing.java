package com.claro.compiler_backends.java_source.monomorphization.ipc;

import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.intermediate_representation.statements.contracts.ContractProcedureImplementationStmt;
import com.claro.intermediate_representation.types.Types;
import com.claro.module_system.module_serialization.proto.claro_types.TypeProtos;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Random;

public class MonomorphizationRequestProcessing {

  private static final Random random = new Random();

  public static String handleMonomorphizationRequest(String base64EncodedMonomorphizationRequest) {
    MonomorphizationRequest monomorphizationRequest;
    try {
      monomorphizationRequest = MonomorphizationRequest.parseFrom(
          BaseEncoding.base64().decode(base64EncodedMonomorphizationRequest));
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Internal Compiler Error! Failed to parse MonomorphizationRequest!", e);
    }

    return BaseEncoding.base64().encode(
        IPCMessages.MonomorphizationResponse.newBuilder()
            .addAllLocalModuleMonomorphizations(
                // TODO(steving) Swap this out with a real implementation that hooks into the actual compiler.
                getTestSimulateCollectLocalMonomorphizationsForMonomorphizationRequest(monomorphizationRequest))
            .putAllTransitiveDepModuleMonomorphizationRequests(
                // TODO(steving) Swap this out with a real implementation that hooks into the actual compiler.
                getTestSimulateCollectTransitiveMonomorphizationsForMonomorphizationRequest().stream().collect(
                    ImmutableMap.toImmutableMap(
                        unused -> "TRANSITIVE_DEP_MODULE_" + (random.nextDouble() > 0.5 ? "AAAAA" : "BBBBB"),
                        r -> r
                    )
                ))
            .build().toByteArray());

  }

  public static ImmutableList<IPCMessages.MonomorphizationResponse.Monomorphization>
  getTestSimulateCollectLocalMonomorphizationsForMonomorphizationRequest(
      MonomorphizationRequest monomorphizationRequest) {

    ImmutableList.Builder<IPCMessages.MonomorphizationResponse.Monomorphization> localMonomorphizationsList =
        ImmutableList.builder();
    // No matter what, we will always codegen for the explicitly requested monomorphization.
    localMonomorphizationsList.add(
        IPCMessages.MonomorphizationResponse.Monomorphization.newBuilder()
            .setMonomorphizationRequest(monomorphizationRequest)
            .setMonomorphizationCodegen(
                // TODO(steving) I need to do a lot more work here than just a simple name generation,
                //  but this is representative.
                ContractProcedureImplementationStmt.getCanonicalProcedureName(
                    "$MONOMORPHIZATION"/*contractName=*/,
                    monomorphizationRequest.getConcreteTypeParamsList().stream()
                        .map(Types::parseTypeProto)
                        .collect(ImmutableList.toImmutableList()),
                    monomorphizationRequest.getProcedureName()
                ))
            .build());

    // I want to simulate some likelihood that there's a transitive local monomorphization that needs to get codegen'd.
    double rand = random.nextDouble();
    if (rand > 0.5) {
      // Here it turns out that there was some random chance I needed to monomorphize some other local generic procedure.
      String procedureName = "LOCAL_RANDOM_" + rand;
      ImmutableList<TypeProtos.TypeProto> concreteTypeParams =
          ImmutableList.of(
              TypeProtos.TypeProto.newBuilder()
                  .setAtom(TypeProtos.AtomType.newBuilder()
                               .setName("RAND_A")
                               .setDefiningModuleDisambiguator(""))
                  .build());
      localMonomorphizationsList.add(
          IPCMessages.MonomorphizationResponse.Monomorphization.newBuilder()
              .setMonomorphizationRequest(
                  MonomorphizationRequest.newBuilder()
                      .setProcedureName(procedureName)
                      .addAllConcreteTypeParams(concreteTypeParams)
                      .build())
              .setMonomorphizationCodegen(
                  // TODO(steving) I need to do a lot more work here than just a simple name generation,
                  //  but this is representative.
                  ContractProcedureImplementationStmt.getCanonicalProcedureName(
                      "$MONOMORPHIZATION"/*contractName=*/,
                      concreteTypeParams.stream().map(Types::parseTypeProto).collect(ImmutableList.toImmutableList()),
                      procedureName
                  ))
              .build());
    }

    return localMonomorphizationsList.build();
  }

  private static ImmutableList<MonomorphizationRequest> getTestSimulateCollectTransitiveMonomorphizationsForMonomorphizationRequest() {
    // Now again some random chance that I may also need to codegen some random transitive dep module monomorphization.
    // This is something that would have to be handled by the coordinator via a new subprocess b/c the source code isn't
    // available here.
    double rand = random.nextDouble();
//    if (rand > 0.7) {
//      System.out.println("TESTING! REQUESTING TRANSITIVE MODULE MONOMORPHIZATION");
//      return ImmutableList.of(
//          MonomorphizationRequest.newBuilder()
//              // I want some chance of collision, to just validate that that works.
//              .setProcedureName("TRANSITIVE_RANDOM_" + (rand > .85 ? "AAAAAA" : "BBBBBB"))
//              .addAllConcreteTypeParams(
//                  ImmutableList.of(
//                      TypeProtos.TypeProto.newBuilder()
//                          .setAtom(TypeProtos.AtomType.newBuilder()
//                                       .setName("RAND_A")
//                                       .setDefiningModuleDisambiguator(""))
//                          .build()))
//              .build()
//      );
//    }
    return ImmutableList.of();
  }
}
