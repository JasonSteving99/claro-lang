package com.claro.compiler_backends.java_source.monomorphization.ipc;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.compiler_backends.java_source.JavaSourceCompilerBackend;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages;
import com.claro.compiler_backends.java_source.monomorphization.proto.ipc_protos.IPCMessages.MonomorphizationRequest;
import com.claro.intermediate_representation.Node;
import com.claro.intermediate_representation.statements.GenericFunctionDefinitionStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

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
                getLocalMonomorphizationsForMonomorphizationRequest(monomorphizationRequest))
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

  @SuppressWarnings("unchecked")
  public static ImmutableList<IPCMessages.MonomorphizationResponse.Monomorphization>
  getLocalMonomorphizationsForMonomorphizationRequest(
      MonomorphizationRequest monomorphizationRequest) {
    ImmutableList<String> requestedProcedureGenericTypeParamNames =
        ((Types.ProcedureType) JavaSourceCompilerBackend.scopedHeap
            .getValidatedIdentifierType(monomorphizationRequest.getProcedureName()))
            .getGenericProcedureArgNames().get();
    ((BiFunction<ScopedHeap, ImmutableMap<Type, Type>, String>)
         JavaSourceCompilerBackend.scopedHeap.getIdentifierValue(monomorphizationRequest.getProcedureName()))
        .apply(
            JavaSourceCompilerBackend.scopedHeap,
            IntStream.range(0, monomorphizationRequest.getConcreteTypeParamsCount()).boxed().collect(
                ImmutableMap.toImmutableMap(
                    i -> Types.$GenericTypeParam.forTypeParamName(requestedProcedureGenericTypeParamNames.get(i)),
                    i -> Types.parseTypeProto(monomorphizationRequest.getConcreteTypeParams(i))
                ))
        );
    // TODO(steving) This is some unreliable hackery. Factor out the core monomorphization logic into a static function
    //   that can be called in isolation w/o automatically recursing over transitive local monomorphizations.
    Node.GeneratedJavaSource monoCodegen =
        new GenericFunctionDefinitionStmt(null, null, null, null, null, null, null, null)
            .generateJavaSourceOutput(JavaSourceCompilerBackend.scopedHeap);
    // TODO(steving) HANDLE LOCAL TRANSITIVE MONOMORPHIZATIONS.
    return ImmutableList.of(
        IPCMessages.MonomorphizationResponse.Monomorphization.newBuilder()
            .setMonomorphizationRequest(monomorphizationRequest)
            .setMonomorphizationCodegen(
                monoCodegen.optionalStaticPreambleStmts().get().toString() +
                monoCodegen.optionalStaticDefinitions().get().toString()
            ).build());
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
