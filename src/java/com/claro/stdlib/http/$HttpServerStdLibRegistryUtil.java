package com.claro.stdlib.http;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.types.BaseType;
import com.claro.intermediate_representation.types.ClaroTypeException;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.Types;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

public class $HttpServerStdLibRegistryUtil {

  public static String getProcedureName() {
    return "startServerAndAwaitShutdown";
  }

  public static Type getProcedureType() {
    return Types.ProcedureType.ConsumerType.forConsumerArgTypes(
        ImmutableList.of(Types.HttpServerType.forHttpService(Types.$GenericTypeParam.forTypeParamName("T"))),
        BaseType.CONSUMER_FUNCTION,
        ImmutableSet.of(),
        new Stmt(ImmutableList.of()) {
          @Override
          public void assertExpectedExprTypes(ScopedHeap scopedHeap) throws ClaroTypeException {
            // Synthetic node, this can't fail.
          }

          @Override
          public GeneratedJavaSource generateJavaSourceOutput(ScopedHeap scopedHeap) {
            return GeneratedJavaSource.forJavaSourceBody(new StringBuilder());
          }

          @Override
          public Object generateInterpretedOutput(ScopedHeap scopedHeap) {
            return null;
          }
        },
        /*explicitlyAnnotatedBlocking=*/false,
        /*genericBlockingOnArgs=*/Optional.empty(),
        Optional.of(ImmutableList.of("T")),
        /*optionalRequiredContractNamesToGenericArgs=*/Optional.of(ImmutableListMultimap.of())
    );
  }
}
