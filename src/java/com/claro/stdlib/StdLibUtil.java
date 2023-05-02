package com.claro.stdlib;

import com.claro.compiler_backends.interpreted.ScopedHeap;
import com.claro.intermediate_representation.statements.Stmt;
import com.claro.intermediate_representation.statements.user_defined_type_def_stmts.NewTypeDefStmt;
import com.claro.intermediate_representation.types.Type;
import com.claro.intermediate_representation.types.TypeProvider;
import com.claro.intermediate_representation.types.Types;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

public class StdLibUtil {

  public static ImmutableList<Stmt> registerIdentifiers(ScopedHeap scopedHeap) {
    for (Table.Cell<String, Type, Types.ProcedureType.ProcedureWrapper> stdLibProcedure
        : StdLibRegistry.stdLibProcedureTypes.cellSet()) {
      registerStdLibProcedure(
          scopedHeap, stdLibProcedure.getRowKey(), stdLibProcedure.getColumnKey(), stdLibProcedure.getValue());
    }

    // These Stmts will get automatically prefixed to the beginning of the program to setup the "stdlib".
    return ImmutableList.of(
        new NewTypeDefStmt("Error", TypeProvider.Util.getTypeByName("T", /*isTypeDefinition=*/true), ImmutableList.of("T")),
        new NewTypeDefStmt(
            "ParsedJson",
            (TypeProvider) (scopedHeap1) ->
                Types.StructType.forFieldTypes(
                    ImmutableList.of("result", "rawJson"),
                    ImmutableList.of(
                        Types.OneofType.forVariantTypes(
                            ImmutableList.of(
                                TypeProvider.Util.getTypeByName("T", /*isTypeDefinition=*/true)
                                    .resolveType(scopedHeap1),
                                Types.UserDefinedType.forTypeNameAndParameterizedTypes("Error", ImmutableList.of(Types.STRING))
                            )
                        ),
                        Types.STRING
                    ),
                    /*isMutable=*/false
                ),
            ImmutableList.of("T")
        )
    );
  }

  private static void registerStdLibProcedure(
      ScopedHeap scopedHeap,
      String procedureName,
      Type procedureType,
      Types.ProcedureType.ProcedureWrapper procedureWrapper) {
    // Validate that this is not a redeclaration of an identifier.
    Preconditions.checkState(
        !scopedHeap.isIdentifierDeclared(procedureName),
        String.format(
            "Internal Compiler Error: Unexpected redeclaration of std-lib procedure %s %s.",
            procedureType,
            procedureName
        )
    );

    // Finally mark the function declared and initialized within the original calling scope.
    scopedHeap.putIdentifierValue(procedureName, procedureType, procedureWrapper);
  }
}
